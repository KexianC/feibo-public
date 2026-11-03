package com.hzmwmt.infrastructure.gateway.digital;

import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Issues and verifies short-lived callback credentials scoped to one provider task.
 *
 * <p>The secret is sent only in that task's callback URL. Redis stores its SHA-256
 * digest, the provider task binding, and a terminal tombstone. Transitions preserve
 * the original TTL, so callbacks cannot extend credential lifetime.</p>
 */
@Component
public class DigitalHumanCallbackVerifier {
    private static final String KEY_PREFIX = "feibo:digital-human:callback:";
    private static final String PENDING_PREFIX = "P:";
    private static final String ACTIVE_PREFIX = "A:";
    private static final String PROCESSING_PREFIX = "W:";
    private static final String TERMINAL_PREFIX = "T:";
    private static final Pattern CALLBACK_ID = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern CALLBACK_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern TASK_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    static final String TRANSITION_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[2] then
              return 2
            end
            if current ~= ARGV[1] then
              return 0
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
            return 1
            """;

    static final String CLAIM_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              local ttl = redis.call('PTTL', KEYS[1])
              if ttl <= 0 then
                return 0
              end
              redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
              return 1
            end
            if current == ARGV[2] then
              return 2
            end
            if current == ARGV[3] then
              return 3
            end
            if current == ARGV[4] then
              return 4
            end
            return 0
            """;

    private final RedissonClient redissonClient;
    private final DigitalHumanApiProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public DigitalHumanCallbackVerifier(
            RedissonClient redissonClient,
            DigitalHumanApiProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    public IssuedCredential issue() {
        Duration ttl = validatedTtl();
        for (int attempt = 0; attempt < 3; attempt++) {
            String id = randomBase64Url(16);
            String token = randomBase64Url(32);
            String pending = pendingState(token);
            try {
                boolean created = bucket(id).trySet(
                        pending, ttl.toMillis(), TimeUnit.MILLISECONDS);
                if (created) {
                    return new IssuedCredential(id, token);
                }
            } catch (Exception e) {
                throw unavailable(e);
            }
        }
        throw unavailable(new IllegalStateException("Unable to allocate callback credential"));
    }

    /** Bind a freshly issued credential to the provider task, exactly once. */
    public void bind(IssuedCredential credential, String taskId) {
        if (credential == null || !validTaskId(taskId)) {
            throw unavailable(new IllegalArgumentException("Invalid callback task binding"));
        }
        String digest = sha256Base64Url(credential.token());
        long result = transition(
                credential.id(),
                PENDING_PREFIX + digest,
                ACTIVE_PREFIX + digest + ":" + taskId);
        if (result != 1L && result != 2L) {
            throw unavailable(new IllegalStateException("Callback credential binding was lost"));
        }
    }

    /**
     * Atomically claims an active credential before any provider-controlled URL
     * is downloaded. Concurrent retries observe PROCESSING and cannot repeat the
     * expensive work. A callback that raced task binding receives a retryable
     * failure, while terminal retries remain idempotent.
     */
    public ClaimResult claim(String id, String suppliedToken, String taskId) {
        if (!validCredentialShape(id, suppliedToken) || !validTaskId(taskId)) {
            throw unauthorized();
        }
        String digest = sha256Base64Url(suppliedToken);
        String active = ACTIVE_PREFIX + digest + ":" + taskId;
        String processing = PROCESSING_PREFIX + digest + ":" + taskId;
        String terminal = TERMINAL_PREFIX + digest + ":" + taskId;
        long result;
        try {
            Long evaluated = redissonClient.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE,
                    CLAIM_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    List.of(key(id)),
                    active,
                    processing,
                    terminal,
                    PENDING_PREFIX + digest);
            result = evaluated == null ? 0L : evaluated;
        } catch (Exception e) {
            throw unavailable(e);
        }
        return switch ((int) result) {
            case 1 -> ClaimResult.CLAIMED;
            case 2 -> ClaimResult.PROCESSING;
            case 3 -> ClaimResult.TERMINAL;
            case 4 -> throw unavailable(new IllegalStateException("Callback task binding is pending"));
            default -> throw unauthorized();
        };
    }

    /** Mark the task terminal while retaining a short-lived tombstone for provider retries. */
    public void markTerminal(String id, String suppliedToken, String taskId) {
        if (!validCredentialShape(id, suppliedToken) || !validTaskId(taskId)) {
            throw unauthorized();
        }
        String digest = sha256Base64Url(suppliedToken);
        long result = transition(
                id,
                PROCESSING_PREFIX + digest + ":" + taskId,
                TERMINAL_PREFIX + digest + ":" + taskId);
        if (result != 1L && result != 2L) {
            throw unavailable(new IllegalStateException("Callback credential could not be revoked"));
        }
    }

    /**
     * Releases only this task's processing state after a safe processing
     * failure. A terminal state can never be reopened by this transition.
     */
    public void release(String id, String suppliedToken, String taskId) {
        if (!validCredentialShape(id, suppliedToken) || !validTaskId(taskId)) {
            throw unauthorized();
        }
        String digest = sha256Base64Url(suppliedToken);
        long result = transition(
                id,
                PROCESSING_PREFIX + digest + ":" + taskId,
                ACTIVE_PREFIX + digest + ":" + taskId);
        if (result != 1L && result != 2L) {
            throw unavailable(new IllegalStateException("Callback credential could not be released"));
        }
    }

    private long transition(String id, String expected, String replacement) {
        try {
            Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE,
                    TRANSITION_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    List.of(key(id)),
                    expected,
                    replacement);
            return result == null ? 0L : result;
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private RBucket<String> bucket(String id) {
        return redissonClient.getBucket(key(id), StringCodec.INSTANCE);
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }

    private String pendingState(String token) {
        return PENDING_PREFIX + sha256Base64Url(token);
    }

    private Duration validatedTtl() {
        Duration ttl = properties.getCallbackCredentialTtl();
        if (ttl == null
                || ttl.compareTo(Duration.ofMinutes(1)) < 0
                || ttl.compareTo(Duration.ofDays(7)) > 0) {
            throw unavailable(new IllegalStateException(
                    "Digital-human callback credential TTL must be between 1 minute and 7 days"));
        }
        return ttl;
    }

    private String randomBase64Url(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean validCredentialShape(String id, String token) {
        return id != null
                && token != null
                && CALLBACK_ID.matcher(id).matches()
                && CALLBACK_TOKEN.matcher(token).matches();
    }

    private boolean validTaskId(String taskId) {
        return taskId != null && TASK_ID.matcher(taskId).matches();
    }

    private AppException unauthorized() {
        return new AppException(ResponseCode.UNAUTHORIZED);
    }

    private AppException unavailable(Exception cause) {
        return new AppException(
                ResponseCode.SERVICE_ERROR.getCode(),
                "Digital-human callback authentication is temporarily unavailable",
                cause);
    }

    public enum ClaimResult {
        CLAIMED,
        PROCESSING,
        TERMINAL
    }

    public record IssuedCredential(String id, String token) {
        public IssuedCredential {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(token, "token");
        }
    }
}
