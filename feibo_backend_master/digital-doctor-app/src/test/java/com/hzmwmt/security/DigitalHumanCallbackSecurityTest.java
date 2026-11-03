package com.hzmwmt.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hzmwmt.api.dto.DigitalHumanVideoCallbackRequestDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.mapper.user.AdminDigitalWorkMapper;
import com.hzmwmt.infrastructure.gateway.digital.DigitalHumanCallbackVerifier;
import com.hzmwmt.infrastructure.gateway.digital.SafeVideoDownloader;
import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.trigger.http.client.DigitalHumanController;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalHumanCallbackSecurityTest {
    private DigitalHumanController controller;
    private DigitalHumanCallbackVerifier verifier;
    private AdminDigitalWorkMapper workMapper;
    private SafeVideoDownloader downloader;
    private FileStorageGateway fileStorageGateway;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "callback-security-test"),
                TDigitalWork.class);
        controller = new DigitalHumanController();
        verifier = mock(DigitalHumanCallbackVerifier.class);
        workMapper = mock(AdminDigitalWorkMapper.class);
        downloader = mock(SafeVideoDownloader.class);
        fileStorageGateway = mock(FileStorageGateway.class);
        ReflectionTestUtils.setField(controller, "callbackVerifier", verifier);
        ReflectionTestUtils.setField(controller, "adminDigitalWorkMapper", workMapper);
        ReflectionTestUtils.setField(controller, "safeVideoDownloader", downloader);
        ReflectionTestUtils.setField(controller, "fileStorageGateway", fileStorageGateway);
        ReflectionTestUtils.setField(controller, "digitalHumanApiProperties", new DigitalHumanApiProperties());
    }

    @Test
    void invalidAuthenticationFailsBeforeDatabaseOrNetwork() throws Exception {
        when(verifier.claim(any(), any(), any()))
                .thenThrow(new AppException(ResponseCode.UNAUTHORIZED));

        ResponseEntity<Response<Void>> response = controller.receiveVideoCallback(
                "bad-id", "wrong", successCallback());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(workMapper, never()).selectOne(any());
        verify(downloader, never()).download(anyString());
    }

    @Test
    void authenticatedCallbackRacingTaskPersistenceGetsRetryableFailure() throws Exception {
        when(verifier.claim(anyString(), anyString(), anyString()))
                .thenReturn(DigitalHumanCallbackVerifier.ClaimResult.CLAIMED);
        when(workMapper.selectOne(any())).thenReturn(null);

        ResponseEntity<Response<Void>> response = controller.receiveVideoCallback(
                "callback-id", "valid", successCallback());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verify(downloader, never()).download(anyString());
        verify(verifier).release("callback-id", "valid", "task_123");
    }

    @Test
    void terminalCredentialAcknowledgesProviderRetryWithoutDatabaseOrNetwork() throws Exception {
        when(verifier.claim(anyString(), anyString(), anyString()))
                .thenReturn(DigitalHumanCallbackVerifier.ClaimResult.TERMINAL);

        ResponseEntity<Response<Void>> response = controller.receiveVideoCallback(
                "callback-id", "valid", successCallback());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(workMapper, never()).selectOne(any());
        verify(downloader, never()).download(anyString());
        verify(verifier, never()).markTerminal(anyString(), anyString(), anyString());
    }

    @Test
    void activeRepeatedTerminalSuccessIsIdempotentAndRevokesCredential() throws Exception {
        when(verifier.claim(anyString(), anyString(), anyString()))
                .thenReturn(DigitalHumanCallbackVerifier.ClaimResult.CLAIMED);
        TDigitalWork existing = new TDigitalWork();
        existing.setUserId(42L);
        existing.setStatus(1);
        when(workMapper.selectOne(any())).thenReturn(existing);

        ResponseEntity<Response<Void>> response = controller.receiveVideoCallback(
                "callback-id", "valid", successCallback());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(downloader, never()).download(anyString());
        verify(verifier).markTerminal("callback-id", "valid", "task_123");
        verify(verifier, never()).release(anyString(), anyString(), anyString());
    }

    @Test
    void repeatedInProgressCallbacksRemainActiveForLaterTerminalCallback() throws Exception {
        when(verifier.claim(anyString(), anyString(), anyString()))
                .thenReturn(DigitalHumanCallbackVerifier.ClaimResult.CLAIMED);
        TDigitalWork existing = new TDigitalWork();
        existing.setUserId(42L);
        when(workMapper.selectOne(any())).thenReturn(existing);
        DigitalHumanVideoCallbackRequestDTO callback = successCallback();
        callback.setStatus("processing");
        callback.setResultUrl(null);

        ResponseEntity<Response<Void>> first = controller.receiveVideoCallback(
                "callback-id", "valid", callback);
        ResponseEntity<Response<Void>> second = controller.receiveVideoCallback(
                "callback-id", "valid", callback);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        verify(workMapper, times(2)).selectOne(any());
        verify(verifier, times(2)).release("callback-id", "valid", "task_123");
        verify(verifier, never()).markTerminal(anyString(), anyString(), anyString());
        verify(downloader, never()).download(anyString());
    }

    @Test
    void processingCredentialReturnsAcceptedWithoutDatabaseOrNetwork() throws Exception {
        when(verifier.claim(anyString(), anyString(), anyString()))
                .thenReturn(DigitalHumanCallbackVerifier.ClaimResult.PROCESSING);

        ResponseEntity<Response<Void>> response = controller.receiveVideoCallback(
                "callback-id", "valid", successCallback());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("Digital-human callback is already processing", response.getBody().getInfo());
        verify(workMapper, never()).selectOne(any());
        verify(downloader, never()).download(anyString());
        verify(fileStorageGateway, never()).upload(any());
    }

    @Test
    void concurrentCallbacksClaimOnceAndPerformExpensiveWorkOnce() throws Exception {
        int concurrency = 10;
        CyclicBarrier claimBarrier = new CyclicBarrier(concurrency);
        AtomicBoolean claimed = new AtomicBoolean();
        when(verifier.claim(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            claimBarrier.await(5, TimeUnit.SECONDS);
            return claimed.compareAndSet(false, true)
                    ? DigitalHumanCallbackVerifier.ClaimResult.CLAIMED
                    : DigitalHumanCallbackVerifier.ClaimResult.PROCESSING;
        });

        TDigitalWork existing = new TDigitalWork();
        existing.setUserId(42L);
        existing.setIsPreview(false);
        when(workMapper.selectOne(any())).thenReturn(existing);
        when(workMapper.update(any(TDigitalWork.class), any())).thenReturn(1);

        Path testTempDirectory = Path.of("target", "callback-test-tmp");
        Files.createDirectories(testTempDirectory);
        Path downloaded = Files.createTempFile(
                testTempDirectory, "digital-human-callback-", ".mp4");
        Files.write(downloaded, new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'});
        when(downloader.download(anyString())).thenReturn(
                new SafeVideoDownloader.DownloadedVideo(
                        downloaded, "video/mp4", ".mp4", Files.size(downloaded)));
        when(fileStorageGateway.upload(any())).thenReturn("https://oss.example/work/task_123.mp4");

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<ResponseEntity<Response<Void>>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(() -> controller.receiveVideoCallback(
                        "callback-id", "valid", successCallback())));
            }

            List<ResponseEntity<Response<Void>>> responses = new ArrayList<>();
            for (Future<ResponseEntity<Response<Void>>> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            assertEquals(1, responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.OK)
                    .count());
            assertEquals(concurrency - 1, responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.ACCEPTED)
                    .count());
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(downloaded);
        }

        verify(downloader, times(1)).download(anyString());
        verify(fileStorageGateway, times(1)).upload(any());
        verify(workMapper, times(1)).update(any(TDigitalWork.class), any());
        verify(verifier, times(1)).markTerminal("callback-id", "valid", "task_123");
        verify(verifier, never()).release(anyString(), anyString(), anyString());
    }

    private DigitalHumanVideoCallbackRequestDTO successCallback() {
        DigitalHumanVideoCallbackRequestDTO callback = new DigitalHumanVideoCallbackRequestDTO();
        callback.setTaskId("task_123");
        callback.setStatus("suc");
        callback.setResultUrl("https://cdn.provider.example/result.mp4");
        callback.setDuration(60_000L);
        return callback;
    }
}
