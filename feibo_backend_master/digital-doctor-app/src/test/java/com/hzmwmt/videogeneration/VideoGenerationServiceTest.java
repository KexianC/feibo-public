package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoGenerationServiceTest {
    private JdbcTemplate db;
    private VideoGenerationService service;
    private VideoProviderGateway gateway;
    private IEntitlementService entitlements;
    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var schema = new ResourceDatabasePopulator(new ClassPathResource("db/video-generation.sql"));
        schema.execute(source); schema.execute(source); // migration must be rerunnable
        db = new JdbcTemplate(source);
        // The current base schema already includes the membership reservation key.
        gateway = mock(VideoProviderGateway.class);
        entitlements = mock(IEntitlementService.class);
        service = new VideoGenerationService(db, new AIModelApiKeyCipher(Base64.getEncoder().encodeToString(new byte[32])), gateway, new DataSourceTransactionManager(source), entitlements);
        when(gateway.submit(anyString(), anyString(), anyString(), any())).thenReturn("upstream-1");
    }
    private VideoGenerationService.Create request() { return new VideoGenerationService.Create(UUID.randomUUID().toString(), "seedance", "森林里的清晨", 5, "720P", "16:9"); }
    private void enable(int limit) { service.save("seedance", new VideoGenerationService.Settings("Seedance", "seedance-test", true, "test-only-key", limit)); }
    @Test void disabledModelsAreNotExposedAndCannotGenerate() {
        assertThat(service.providers(false)).isEmpty();
        assertThat(service.providers(true)).hasSize(2);
        assertThatThrownBy(() -> service.create("101", request())).hasMessageContaining("尚未启用");
        verifyNoInteractions(gateway);
    }
    @Test void keyIsEncryptedAndBlankPreservesIt() {
        enable(10);
        String encrypted = db.queryForObject("SELECT api_key_ciphertext FROM ai_video_provider WHERE provider='seedance'", String.class);
        assertThat(encrypted).startsWith("v1:").doesNotContain("test-only-key");
        service.save("seedance", new VideoGenerationService.Settings("New name", "seedance-test", true, "", 8));
        assertThat(db.queryForObject("SELECT api_key_ciphertext FROM ai_video_provider WHERE provider='seedance'", String.class)).isEqualTo(encrypted);
        assertThat(service.providers(false)).hasSize(1);
    }
    @Test void enablingWithoutKeyIsRejected() {
        assertThatThrownBy(() -> service.save("minimax", new VideoGenerationService.Settings("MiniMax", "MiniMax-Hailuo-2.3", true, "", 10))).hasMessageContaining("API Key");
    }
    @Test void sameRequestCreatesOnlyOnePaidTask() {
        enable(10); var request = request();
        var first = service.create("101", request);
        assertThat(service.create("101", request).id()).isEqualTo(first.id());
        verify(gateway, times(1)).submit(anyString(), anyString(), eq("test-only-key"), any());
        assertThat(service.list("101")).hasSize(1);
    }
    @Test void changedPayloadCannotReuseRequestId() {
        enable(10); var r = request(); service.create("101", r);
        assertThatThrownBy(() -> service.create("101", new VideoGenerationService.Create(r.requestId(), r.provider(), "different", 5, "720P", "16:9"))).hasMessageContaining("其他任务");
    }
    @Test void ownerCannotReadAnotherUsersTask() {
        enable(10); var task = service.create("101", request());
        assertThat(service.list("102")).isEmpty();
        assertThatThrownBy(() -> service.refresh("102", task.id())).hasMessageContaining("访问权限");
        verify(gateway, never()).query(anyString(), anyString(), anyString());
    }
    @Test void globalQuotaIncludesOtherUsersAndUnknownSubmissions() {
        enable(1); when(gateway.submit(anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("timeout"));
        var first = service.create("101", request());
        assertThat(first.status()).isEqualTo("unknown");
        assertThatThrownBy(() -> service.create("102", request())).hasMessageContaining("上限");
    }
    @Test void threeActiveTasksBlockFurtherSubmission() {
        enable(10); service.create("101", request()); service.create("101", request()); service.create("101", request());
        assertThatThrownBy(() -> service.create("101", request())).hasMessageContaining("3 个任务");
    }
    @Test void successfulAndFailedResultsArePersisted() {
        enable(10); var task = service.create("101", request());
        when(gateway.query(anyString(), anyString(), anyString())).thenReturn(new VideoProviderGateway.Progress("succeeded", "https://example.com/video.mp4", null));
        assertThat(service.refresh("101", task.id()).videoUrl()).isEqualTo("https://example.com/video.mp4");
        assertThat(service.list("101").get(0).status()).isEqualTo("succeeded");
        var failed = service.create("101", request());
        when(gateway.query(anyString(), anyString(), anyString())).thenReturn(new VideoProviderGateway.Progress("failed", null, "failed"));
        assertThat(service.refresh("101", failed.id()).status()).isEqualTo("failed");
    }
    @Test void invalidMiniMaxCombinationNeverCallsProvider() {
        assertThatThrownBy(() -> service.create("101", new VideoGenerationService.Create(UUID.randomUUID().toString(), "minimax", "test", 10, "1080P", "16:9"))).hasMessageContaining("1080P");
        verifyNoInteractions(gateway);
    }
    @Test void insufficientMembershipQuotaNeverSubmitsOrLeavesATask() {
        enable(10);
        doThrow(new IllegalArgumentException("AI视频额度不足")).when(entitlements)
                .reserve(eq(101L), eq(IEntitlementService.AI_VIDEO_SECONDS), eq(5L), anyString());
        assertThatThrownBy(() -> service.create("101", request())).hasMessageContaining("额度不足");
        verifyNoInteractions(gateway);
        assertThat(service.list("101")).isEmpty();
    }
    @Test void duplicateRequestReservesOnlyOnceAndUnknownSubmissionKeepsQuota() {
        enable(10); var request = request();
        when(gateway.submit(anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("timeout"));
        var task = service.create("101", request);
        assertThat(service.create("101", request).id()).isEqualTo(task.id());
        verify(entitlements, times(1)).reserve(eq(101L), eq(IEntitlementService.AI_VIDEO_SECONDS), eq(5L), anyString());
        verify(entitlements, never()).release(anyLong(), anyString());
    }
    @Test void failedResultRefundsOnceAndCannotExposeASuccessLater() {
        enable(10); var task = service.create("101", request());
        when(gateway.query(anyString(), anyString(), anyString())).thenReturn(new VideoProviderGateway.Progress("failed", null, "provider failure"));
        service.refresh("101", task.id());
        service.refresh("101", task.id());
        verify(entitlements, times(1)).release(101L, "ai-video:" + task.id());
        verify(entitlements, never()).commit(anyLong(), anyString());
        assertThat(service.list("101").get(0).videoUrl()).isNull();
    }
    @Test void completedResultRequiresQuotaSettlementAndCannotBeDowngraded() {
        enable(10); var task = service.create("101", request());
        when(gateway.query(anyString(), anyString(), anyString())).thenReturn(new VideoProviderGateway.Progress("succeeded", "https://example.com/test.mp4", null));
        service.refresh("101", task.id());
        verify(entitlements).resize(101L, "ai-video:" + task.id(), 5L);
        verify(entitlements).commit(101L, "ai-video:" + task.id());
        when(gateway.query(anyString(), anyString(), anyString())).thenReturn(new VideoProviderGateway.Progress("failed", null, "stale poll"));
        assertThat(service.refresh("101", task.id()).status()).isEqualTo("succeeded");
        verify(entitlements, never()).release(anyLong(), anyString());
    }
}

