package com.hzmwmt.infrastructure.gateway.digital;

import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalHumanCallbackVerifierTest {
    private static final String ID = "I".repeat(22);
    private static final String TOKEN = "T".repeat(43);
    private static final String TASK = "task_123";

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> bucket;
    @Mock
    private RScript script;

    private DigitalHumanCallbackVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new DigitalHumanCallbackVerifier(
                redissonClient, new DigitalHumanApiProperties());
    }

    @Test
    void issuedCredentialIsRandomShortLivedAndOnlyItsHashIsStored() {
        doReturn(bucket).when(redissonClient).getBucket(anyString(), eq(StringCodec.INSTANCE));
        when(bucket.trySet(anyString(), eq(86_400_000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);

        DigitalHumanCallbackVerifier.IssuedCredential credential = verifier.issue();

        assertTrue(credential.id().matches("[A-Za-z0-9_-]{22}"));
        assertTrue(credential.token().matches("[A-Za-z0-9_-]{43}"));
        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        verify(bucket).trySet(state.capture(), eq(86_400_000L), eq(TimeUnit.MILLISECONDS));
        assertEquals("P:" + digest(credential.token()), state.getValue());
        assertFalse(state.getValue().contains(credential.token()));
    }

    @Test
    void bindingIsAtomicAndDoesNotExtendOriginalExpiry() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());

        verifier.bind(new DigitalHumanCallbackVerifier.IssuedCredential(ID, TOKEN), TASK);

        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                eq("P:" + digest(TOKEN)),
                eq("A:" + digest(TOKEN) + ":" + TASK));
        assertTrue(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT.contains("PTTL"));
    }

    @Test
    void activeCredentialIsAtomicallyClaimedForExactlyOneTask() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.CLAIM_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any(),
                any(),
                any());

        assertEquals(
                DigitalHumanCallbackVerifier.ClaimResult.CLAIMED,
                verifier.claim(ID, TOKEN, TASK));

        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.CLAIM_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                eq("A:" + digest(TOKEN) + ":" + TASK),
                eq("W:" + digest(TOKEN) + ":" + TASK),
                eq("T:" + digest(TOKEN) + ":" + TASK),
                eq("P:" + digest(TOKEN)));
        assertTrue(DigitalHumanCallbackVerifier.CLAIM_SCRIPT.contains("PTTL"));
    }

    @Test
    void credentialForAnotherTaskFailsClosed() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(0L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.CLAIM_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any(),
                any(),
                any());

        AppException crossTask = assertThrows(
                AppException.class,
                () -> verifier.claim(ID, TOKEN, "task_999"));
        assertEquals(ResponseCode.UNAUTHORIZED.getCode(), crossTask.getCode());
    }

    @Test
    void pendingCredentialGetsRetryableFailureAndMissingStateFailsClosed() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(4L, 0L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.CLAIM_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any(),
                any(),
                any());

        AppException pending = assertThrows(
                AppException.class,
                () -> verifier.claim(ID, TOKEN, TASK));
        assertEquals(ResponseCode.SERVICE_ERROR.getCode(), pending.getCode());

        AppException expired = assertThrows(
                AppException.class,
                () -> verifier.claim(ID, TOKEN, TASK));
        assertEquals(ResponseCode.UNAUTHORIZED.getCode(), expired.getCode());
    }

    @Test
    void processingAndTerminalCredentialsAreDistinguishedWithoutReclaiming() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(2L, 3L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.CLAIM_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any(),
                any(),
                any());

        assertEquals(
                DigitalHumanCallbackVerifier.ClaimResult.PROCESSING,
                verifier.claim(ID, TOKEN, TASK));
        assertEquals(
                DigitalHumanCallbackVerifier.ClaimResult.TERMINAL,
                verifier.claim(ID, TOKEN, TASK));
    }

    @Test
    void terminalTransitionIsAtomicAndIdempotent() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L, 2L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());

        verifier.markTerminal(ID, TOKEN, TASK);
        verifier.markTerminal(ID, TOKEN, TASK);

        verify(script, org.mockito.Mockito.times(2)).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                eq("W:" + digest(TOKEN) + ":" + TASK),
                eq("T:" + digest(TOKEN) + ":" + TASK));
    }

    @Test
    void failedProcessingCanOnlyReleaseItsOwnClaimBackToActive() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());

        verifier.release(ID, TOKEN, TASK);

        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                eq(DigitalHumanCallbackVerifier.TRANSITION_SCRIPT),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                eq("W:" + digest(TOKEN) + ":" + TASK),
                eq("A:" + digest(TOKEN) + ":" + TASK));
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
