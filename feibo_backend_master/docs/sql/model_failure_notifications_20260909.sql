-- Apply before deploying model fallback notifications. Additive and rerunnable on MySQL 8.
-- Failure records never contain prompts, answers, API keys or raw provider error bodies.
CREATE TABLE IF NOT EXISTS t_ai_model_failure_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NULL,
    conversation_id BIGINT NULL,
    original_model_config_id BIGINT NULL,
    original_provider VARCHAR(100) NULL,
    original_model VARCHAR(200) NULL,
    error_type VARCHAR(100) NOT NULL,
    fallback_provider VARCHAR(100) NULL,
    fallback_model VARCHAR(200) NULL,
    outcome VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_failure_request (request_id),
    KEY idx_ai_model_failure_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_model_failure_notice_read (
    notice_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    read_at DATETIME(3) NOT NULL,
    PRIMARY KEY (admin_user_id, notice_id),
    KEY idx_ai_model_failure_read_notice (notice_id),
    CONSTRAINT fk_ai_model_failure_read_notice FOREIGN KEY (notice_id)
        REFERENCES t_ai_model_failure_notice (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
