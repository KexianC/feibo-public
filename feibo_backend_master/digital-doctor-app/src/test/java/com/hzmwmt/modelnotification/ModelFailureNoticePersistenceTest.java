package com.hzmwmt.modelnotification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelFailureNoticePersistenceTest {
    private ModelFailureNoticeStore store;
    private ModelFailureNoticeService service;
    private IAuthService auth;
    private JdbcTemplate db;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var migration = List.of(
                Path.of("../docs/sql/model_failure_notifications_20260909.sql"),
                Path.of("docs/sql/model_failure_notifications_20260909.sql"),
                Path.of("feibo_backend_master/docs/sql/model_failure_notifications_20260909.sql"))
                .stream().filter(Files::isRegularFile).findFirst().orElseThrow();
        var populator = new ResourceDatabasePopulator(new FileSystemResource(migration));
        // Test the real rerunnable deployment DDL rather than a separate test-only schema.
        populator.execute(source);
        populator.execute(source);
        db = new JdbcTemplate(source);
        store = new ModelFailureNoticeStore(db);
        auth = mock(IAuthService.class);
        when(auth.queryValidatedUser()).thenReturn(AuthUserEntity.builder().userId(101L).build());
        service = new ModelFailureNoticeService(store, auth);
    }

    @Test void duplicateEventsPersistOnceAndKeepIdentifiersLossless() throws Exception {
        var event = event("request-1", "FALLBACK_SUCCEEDED", "HTTP_503");
        store.save(event);
        store.save(event);
        var page = service.list(1, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.unreadCount()).isEqualTo(1);
        var notice = page.records().get(0);
        assertThat(notice.userId()).isEqualTo("9007199254740993");
        assertThat(notice.conversationId()).isEqualTo("9007199254740995");
        assertThat(notice.originalModelConfigId()).isEqualTo("9007199254740997");
        assertThat(notice.read()).isFalse();
        assertThat(notice.createdAt()).endsWith("Z");
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(notice));
        assertThat(json.get("userId").isTextual()).isTrue();
        assertThat(json.get("id").isTextual()).isTrue();
    }

    @Test void individualAndBulkReadStatesAreScopedToEachSuperAdministrator() {
        store.save(event("request-1", "FALLBACK_SUCCEEDED", "HTTP_503"));
        store.save(event("request-2", "FALLBACK_FAILED", "TimeoutException"));
        var first = service.list(1, 20).records().get(0);
        service.markRead(first.id());
        service.markRead(first.id());
        assertThat(service.unreadCount()).isEqualTo(1);
        when(auth.queryValidatedUser()).thenReturn(AuthUserEntity.builder().userId(102L).build());
        assertThat(service.unreadCount()).isEqualTo(2);
        assertThat(service.list(1, 20).records()).allMatch(notice -> !notice.read());
        service.markAllRead();
        service.markAllRead();
        assertThat(service.unreadCount()).isZero();
        when(auth.queryValidatedUser()).thenReturn(AuthUserEntity.builder().userId(101L).build());
        assertThat(service.unreadCount()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_ai_model_failure_notice_read", Long.class)).isEqualTo(3);
        store.save(event("request-3", "FALLBACK_UNAVAILABLE", "IllegalStateException"));
        assertThat(service.unreadCount()).isEqualTo(2);
    }

    @Test void paginationIsStableAndDoesNotOverflowOnLargePageNumbers() {
        store.save(event("request-1", "PRIMARY_FAILED", "HTTP_503"));
        store.save(event("request-2", "FALLBACK_FAILED", "TimeoutException"));
        assertThat(service.list(1, 1).records().get(0).requestId()).isEqualTo("request-2");
        assertThat(service.list(2, 1).records().get(0).requestId()).isEqualTo("request-1");
        assertThat(service.list(Integer.MAX_VALUE, 100).records()).isEmpty();
        assertThatThrownBy(() -> service.list(0, 10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list(1, 101)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void unknownAndInvalidNoticeIdsCannotCreateReadRecords() {
        assertThatThrownBy(() -> service.markRead("1")).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        for (String id : List.of("-1", "0", "1 OR 1=1", "9223372036854775808")) {
            assertThatThrownBy(() -> service.markRead(id)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM t_ai_model_failure_notice_read", Long.class)).isZero();
    }

    @Test void rawProviderErrorsAreDiscardedAndOptionalFallbackIsSupported() {
        store.save(new ModelFailureEvent("request-safe", null, null, null, "original", "model",
                "HTTP 401 Authorization: Bearer sk-secret prompt=private", null, null, "FALLBACK_UNAVAILABLE"));
        var record = service.list(1, 20).records().get(0);
        assertThat(record.errorType()).isEqualTo("ProviderError");
        assertThat(record.userId()).isNull();
        assertThat(record.fallbackModel()).isNull();
        assertThatThrownBy(() -> store.save(event("request-invalid", "unknown-outcome", "HTTP_503")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ModelFailureEvent event(String requestId, String outcome, String errorType) {
        return new ModelFailureEvent(requestId, 9007199254740993L, 9007199254740995L, 9007199254740997L,
                "openai", "gpt-test", errorType, "deepseek", "deepseek-chat", outcome);
    }
}
