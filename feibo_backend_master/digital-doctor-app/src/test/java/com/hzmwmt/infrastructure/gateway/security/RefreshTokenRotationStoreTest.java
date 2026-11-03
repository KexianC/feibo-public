package com.hzmwmt.infrastructure.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationStoreTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RScript script;
    @Mock
    private RBucket<String> tokenBucket;
    @Mock
    private RBucket<String> familyBucket;
    @Mock
    private RBucket<String> legacyFamilyBucket;

    private RefreshTokenRotationStore store;

    @BeforeEach
    void setUp() {
        store = new RefreshTokenRotationStore(redissonClient);
    }

    @Test
    void redisTransitionAllowsOneWinnerAndFlagsCompetingUseAsReplay() {
        doReturn(familyBucket).when(redissonClient)
                .getBucket("token:refresh:family:{family}:state", StringCodec.INSTANCE);
        doReturn(legacyFamilyBucket).when(redissonClient)
                .getBucket("token:refresh:family:family", StringCodec.INSTANCE);
        when(familyBucket.get()).thenReturn(null);
        when(legacyFamilyBucket.get()).thenReturn("old");
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L, -1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(RefreshTokenRotationStore.ROTATE_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());

        assertEquals(
                RefreshTokenRotationStore.RotationResult.ROTATED,
                store.consumeAndRotate("family", "old", "new-a", Duration.ofMinutes(5), false));
        assertEquals(
                RefreshTokenRotationStore.RotationResult.REPLAY_REVOKED,
                store.consumeAndRotate("family", "old", "new-b", Duration.ofMinutes(5), false));

        verify(script, times(2)).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(RefreshTokenRotationStore.ROTATE_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                eq(List.of(
                        "token:refresh:{family}:jti:old",
                        "token:refresh:family:{family}:state")),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void replayObservationRevokesTheWholeFamily() {
        doReturn(tokenBucket).when(redissonClient)
                .getBucket("token:refresh:{family}:jti:old", StringCodec.INSTANCE);
        doReturn(familyBucket).when(redissonClient)
                .getBucket("token:refresh:family:{family}:state", StringCodec.INSTANCE);
        when(familyBucket.get()).thenReturn("old");
        when(tokenBucket.isExists()).thenReturn(true);
        when(tokenBucket.get()).thenReturn("CONSUMED:family");

        assertFalse(store.validate("family", "old", Duration.ofMinutes(5)));

        verify(familyBucket).set("REVOKED", 300_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void statefulTokenFailsClosedWhenFamilyStateIsMissingButLegacyMayMigrate() {
        doReturn(tokenBucket).when(redissonClient)
                .getBucket("token:refresh:{family}:jti:old", StringCodec.INSTANCE);
        doReturn(familyBucket).when(redissonClient)
                .getBucket("token:refresh:family:{family}:state", StringCodec.INSTANCE);
        doReturn(legacyFamilyBucket).when(redissonClient)
                .getBucket("token:refresh:family:family", StringCodec.INSTANCE);
        doReturn(tokenBucket).when(redissonClient)
                .getBucket("token:refreshold", StringCodec.INSTANCE);
        when(tokenBucket.isExists()).thenReturn(false);
        when(familyBucket.get()).thenReturn(null);
        when(legacyFamilyBucket.get()).thenReturn(null);

        assertFalse(store.validate("family", "old", Duration.ofMinutes(5)));
        assertTrue(store.validate(null, "old", Duration.ofMinutes(5)));
    }

    @Test
    void initialFamilyRegistrationIsAtomicAndExpiring() {
        doReturn(familyBucket).when(redissonClient)
                .getBucket("token:refresh:family:{family}:state", StringCodec.INSTANCE);
        when(familyBucket.trySet("jti", 300_000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        assertTrue(store.initializeFamily("family", "jti", Duration.ofMinutes(5)));
    }

    @Test
    void keysForOneFamilyShareAClusterHashTag() {
        assertTrue(RefreshTokenRotationStore.familyKey("family-1").contains("{family-1}"));
        assertTrue(RefreshTokenRotationStore.tokenKey("family-1", "jti-1").contains("{family-1}"));
    }

    @Test
    void existingFamilyStateMigratesBeforeRotation() {
        doReturn(familyBucket).when(redissonClient)
                .getBucket("token:refresh:family:{family}:state", StringCodec.INSTANCE);
        doReturn(legacyFamilyBucket).when(redissonClient)
                .getBucket("token:refresh:family:family", StringCodec.INSTANCE);
        when(familyBucket.get()).thenReturn(null);
        when(legacyFamilyBucket.get()).thenReturn("old");
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(RefreshTokenRotationStore.ROTATE_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(), any(), any(), any(), any(), any(), any());

        assertEquals(
                RefreshTokenRotationStore.RotationResult.ROTATED,
                store.consumeAndRotate("family", "old", "new", Duration.ofMinutes(5), false));

        verify(familyBucket).trySet("old", 300_000L, TimeUnit.MILLISECONDS);
    }
}
