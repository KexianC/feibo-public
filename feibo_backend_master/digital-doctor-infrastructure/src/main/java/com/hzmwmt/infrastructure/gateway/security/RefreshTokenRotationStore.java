package com.hzmwmt.infrastructure.gateway.security;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_REFRESH_FAMILY_PREFIX;
import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_REFRESH_TOKEN_PREFIX;

/**
 * Redis state machine for refresh-token families.
 *
 * <p>The Lua transition is the security boundary: one old jti can move a
 * family to exactly one new jti. Any competing/replayed use revokes the whole
 * family. Tokens issued before family tracking may bootstrap once; tokens that
 * already carry a family id fail closed if their Redis state disappears.</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRotationStore {

    static final String REVOKED = "REVOKED";
    static final String CONSUMED_PREFIX = "CONSUMED:";

    static final String ROTATE_SCRIPT = """
            local family = redis.call('GET', KEYS[2])
            if family == ARGV[4] then
              return -2
            end
            if redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('SET', KEYS[2], ARGV[4], 'PX', ARGV[3])
              return -1
            end
            if not family and ARGV[5] ~= '1' then
              return -3
            end
            if family and family ~= ARGV[1] then
              redis.call('SET', KEYS[2], ARGV[4], 'PX', ARGV[3])
              return -1
            end
            redis.call('SET', KEYS[1], ARGV[6] .. ARGV[2], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """;

    private final RedissonClient redissonClient;

    public boolean initializeFamily(String familyId, String currentJti, Duration ttl) {
        return familyBucket(familyId).trySet(currentJti, positiveMillis(ttl), TimeUnit.MILLISECONDS);
    }

    public RotationResult consumeAndRotate(
            String familyId,
            String oldJti,
            String newJti,
            Duration ttl,
            boolean legacyToken) {
        migrateLegacyFamilyState(familyId, ttl);
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                ROTATE_SCRIPT,
                RScript.ReturnType.INTEGER,
                List.of(tokenKey(familyId, oldJti), familyKey(familyId)),
                oldJti,
                newJti,
                positiveMillis(ttl),
                REVOKED,
                legacyToken ? "1" : "0",
                CONSUMED_PREFIX
        );
        if (Objects.equals(result, 1L)) {
            return RotationResult.ROTATED;
        }
        if (Objects.equals(result, -1L)) {
            return RotationResult.REPLAY_REVOKED;
        }
        if (Objects.equals(result, -2L)) {
            return RotationResult.FAMILY_REVOKED;
        }
        return RotationResult.STATE_MISSING;
    }

    /**
     * Returns false for a revoked token/family. A consumed token is a replay,
     * so observing it also revokes its family before failing.
     */
    public boolean validate(String familyId, String jti, Duration ttl) {
        if (familyId == null) {
            return !legacyTokenBucket(jti).isExists();
        }

        migrateLegacyFamilyState(familyId, ttl);
        RBucket<String> tokenBucket = tokenBucket(familyId, jti);
        if (tokenBucket.isExists()) {
            String state = tokenBucket.get();
            if (state != null && state.startsWith(CONSUMED_PREFIX)) {
                String consumedFamily = state.substring(CONSUMED_PREFIX.length());
                if (!consumedFamily.isBlank()) {
                    revokeFamily(consumedFamily, ttl);
                }
            }
            return false;
        }

        // Preserve revocations/consumption records written before the cluster
        // slot key layout was introduced.
        RBucket<String> legacyTokenBucket = legacyTokenBucket(jti);
        if (legacyTokenBucket.isExists()) {
            String state = legacyTokenBucket.get();
            if (state != null && state.startsWith(CONSUMED_PREFIX)) {
                String consumedFamily = state.substring(CONSUMED_PREFIX.length());
                if (!consumedFamily.isBlank()) {
                    revokeFamily(consumedFamily, ttl);
                }
            }
            return false;
        }
        String familyState = familyBucket(familyId).get();
        return jti.equals(familyState);
    }

    public void revoke(String familyId, String jti, Duration ttl) {
        long ttlMillis = positiveMillis(ttl);
        if (familyId == null) {
            legacyTokenBucket(jti).set(REVOKED, ttlMillis, TimeUnit.MILLISECONDS);
            return;
        }
        migrateLegacyFamilyState(familyId, ttl);
        tokenBucket(familyId, jti).set(REVOKED, ttlMillis, TimeUnit.MILLISECONDS);
        revokeFamily(familyId, ttl);
    }

    private void revokeFamily(String familyId, Duration ttl) {
        familyBucket(familyId).set(REVOKED, positiveMillis(ttl), TimeUnit.MILLISECONDS);
    }

    private RBucket<String> familyBucket(String familyId) {
        return redissonClient.getBucket(familyKey(familyId), StringCodec.INSTANCE);
    }

    private RBucket<String> tokenBucket(String familyId, String jti) {
        return redissonClient.getBucket(tokenKey(familyId, jti), StringCodec.INSTANCE);
    }

    private RBucket<String> legacyFamilyBucket(String familyId) {
        return redissonClient.getBucket(legacyFamilyKey(familyId), StringCodec.INSTANCE);
    }

    private RBucket<String> legacyTokenBucket(String jti) {
        return redissonClient.getBucket(legacyTokenKey(jti), StringCodec.INSTANCE);
    }

    private void migrateLegacyFamilyState(String familyId, Duration ttl) {
        RBucket<String> currentBucket = familyBucket(familyId);
        if (currentBucket.get() != null) {
            return;
        }
        String legacyState = legacyFamilyBucket(familyId).get();
        if (legacyState != null) {
            currentBucket.trySet(legacyState, positiveMillis(ttl), TimeUnit.MILLISECONDS);
        }
    }

    static String familyKey(String familyId) {
        return KEY_REFRESH_FAMILY_PREFIX + slotTag(familyId) + ":state";
    }

    static String tokenKey(String familyId, String jti) {
        return KEY_REFRESH_TOKEN_PREFIX + ":" + slotTag(familyId) + ":jti:" + jti;
    }

    static String legacyFamilyKey(String familyId) {
        return KEY_REFRESH_FAMILY_PREFIX + familyId;
    }

    static String legacyTokenKey(String jti) {
        return KEY_REFRESH_TOKEN_PREFIX + jti;
    }

    private static String slotTag(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            throw new IllegalArgumentException("refresh token family id must not be blank");
        }
        return "{" + familyId + "}";
    }

    private long positiveMillis(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token TTL must be positive");
        }
        return ttl.toMillis();
    }

    public enum RotationResult {
        ROTATED,
        REPLAY_REVOKED,
        FAMILY_REVOKED,
        STATE_MISSING
    }
}
