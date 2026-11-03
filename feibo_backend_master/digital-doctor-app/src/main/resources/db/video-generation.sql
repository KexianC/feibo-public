CREATE TABLE IF NOT EXISTS ai_video_provider (
  id BIGINT PRIMARY KEY,
  provider VARCHAR(24) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  model VARCHAR(160) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  api_key_ciphertext TEXT,
  daily_limit INT NOT NULL DEFAULT 10
);
INSERT INTO ai_video_provider (id, provider, name, model, enabled, daily_limit)
SELECT 91001, 'seedance', 'Seedance', 'doubao-seedance-1-0-pro-250528', FALSE, 10
WHERE NOT EXISTS (SELECT 1 FROM ai_video_provider WHERE provider = 'seedance');
INSERT INTO ai_video_provider (id, provider, name, model, enabled, daily_limit)
SELECT 91002, 'minimax', 'MiniMax 海螺', 'MiniMax-Hailuo-2.3', FALSE, 10
WHERE NOT EXISTS (SELECT 1 FROM ai_video_provider WHERE provider = 'minimax');
CREATE TABLE IF NOT EXISTS ai_video_task (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(100) NOT NULL,
  request_id VARCHAR(36) NOT NULL,
  provider VARCHAR(24) NOT NULL,
  model VARCHAR(160) NOT NULL,
  prompt TEXT NOT NULL,
  duration INT NOT NULL,
  resolution VARCHAR(12) NOT NULL,
  ratio VARCHAR(12) NOT NULL,
  status VARCHAR(24) NOT NULL,
  provider_task_id VARCHAR(160),
  quota_biz_key VARCHAR(128),
  video_url TEXT,
  error_message VARCHAR(240),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (owner_id, request_id)
);
