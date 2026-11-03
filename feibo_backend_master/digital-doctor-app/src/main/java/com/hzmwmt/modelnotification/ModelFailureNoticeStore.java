package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public class ModelFailureNoticeStore {
    private static final Set<String> OUTCOMES = Set.of(
            "FALLBACK_SUCCEEDED", "FALLBACK_FAILED", "FALLBACK_UNAVAILABLE", "PRIMARY_FAILED", "CANCELLED");
    private final JdbcTemplate db;

    public ModelFailureNoticeStore(JdbcTemplate db) { this.db = db; }

    public void save(ModelFailureEvent event) {
        if (event == null || event.requestId() == null || !event.requestId().matches("[A-Za-z0-9_-]{1,64}")
                || event.outcome() == null || !OUTCOMES.contains(event.outcome())) {
            throw new IllegalArgumentException("Invalid model failure event");
        }
        db.update("""
                INSERT INTO t_ai_model_failure_notice
                    (request_id,user_id,conversation_id,original_model_config_id,original_provider,original_model,
                     error_type,fallback_provider,fallback_model,outcome,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE request_id=VALUES(request_id)
                """, event.requestId(), event.userId(), event.conversationId(), event.originalModelConfigId(),
                bounded(event.originalProvider(), 100), bounded(event.originalModel(), 200), safeErrorType(event.errorType()),
                bounded(event.fallbackProvider(), 100), bounded(event.fallbackModel(), 200), event.outcome(),
                Timestamp.from(Instant.now()));
    }

    List<ModelFailureNotice> list(long adminUserId, int size, long offset) {
        return db.query("""
                SELECT n.*, CASE WHEN r.notice_id IS NULL THEN FALSE ELSE TRUE END AS is_read
                FROM t_ai_model_failure_notice n
                LEFT JOIN t_ai_model_failure_notice_read r ON r.notice_id=n.id AND r.admin_user_id=?
                ORDER BY n.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new ModelFailureNotice(
                rs.getString("id"), rs.getString("request_id"), rs.getString("user_id"),
                rs.getString("conversation_id"), rs.getString("original_model_config_id"),
                rs.getString("original_provider"), rs.getString("original_model"), rs.getString("error_type"),
                rs.getString("fallback_provider"), rs.getString("fallback_model"), rs.getString("outcome"),
                rs.getTimestamp("created_at").toInstant().toString(), rs.getBoolean("is_read")),
                adminUserId, size, offset);
    }

    long count() { return db.queryForObject("SELECT COUNT(*) FROM t_ai_model_failure_notice", Long.class); }

    long unreadCount(long adminUserId) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM t_ai_model_failure_notice n
                WHERE NOT EXISTS (SELECT 1 FROM t_ai_model_failure_notice_read r
                                  WHERE r.notice_id=n.id AND r.admin_user_id=?)
                """, Long.class, adminUserId);
    }

    boolean exists(long noticeId) {
        return db.queryForObject("SELECT COUNT(*) FROM t_ai_model_failure_notice WHERE id=?", Long.class, noticeId) > 0;
    }

    void markRead(long adminUserId, long noticeId) {
        db.update("""
                INSERT INTO t_ai_model_failure_notice_read (notice_id,admin_user_id,read_at)
                SELECT id,?,? FROM t_ai_model_failure_notice WHERE id=?
                ON DUPLICATE KEY UPDATE notice_id=VALUES(notice_id)
                """, adminUserId, Timestamp.from(Instant.now()), noticeId);
    }

    void markAllRead(long adminUserId) {
        db.update("""
                INSERT INTO t_ai_model_failure_notice_read (notice_id,admin_user_id,read_at)
                SELECT n.id,?,? FROM t_ai_model_failure_notice n
                WHERE NOT EXISTS (SELECT 1 FROM t_ai_model_failure_notice_read r
                                  WHERE r.notice_id=n.id AND r.admin_user_id=?)
                ON DUPLICATE KEY UPDATE notice_id=VALUES(notice_id)
                """, adminUserId, Timestamp.from(Instant.now()), adminUserId);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }

    private static String safeErrorType(String value) {
        // Only a class/category/HTTP code is allowed, even if a future caller passes raw errors.
        return value != null && value.matches("[A-Za-z0-9_.$:-]{1,100}") ? value : "ProviderError";
    }
}
