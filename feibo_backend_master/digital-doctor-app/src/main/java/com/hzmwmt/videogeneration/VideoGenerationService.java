package com.hzmwmt.videogeneration;

import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Service
public class VideoGenerationService {
    public record Create(@Pattern(regexp="[a-fA-F0-9-]{36}") @NotBlank String requestId,
                         @Pattern(regexp="seedance|minimax") @NotBlank String provider,
                         @NotBlank @Size(max=2000) String prompt,
                         @Min(5) @Max(10) int duration,
                         @Pattern(regexp="720P|1080P") @NotBlank String resolution,
                         @Pattern(regexp="16:9|9:16|1:1") @NotBlank String ratio) {}
    public record Settings(@NotBlank @Size(max=80) String name,
                           @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,160}") String model,
                           boolean enabled, @Size(max=4096) String apiKey,
                           @Min(1) @Max(1000) int dailyLimit) {}
    public record Provider(String provider, String name, String model, boolean enabled, boolean apiKeyConfigured, int dailyLimit) {}
    public record Task(String id, String provider, String model, String prompt, int duration, String resolution,
                       String ratio, String status, String videoUrl, String error, String createdAt) {}
    private record Config(long id, Provider view, String ciphertext) {}
    private final JdbcTemplate db;
    private final AIModelApiKeyCipher cipher;
    private final VideoProviderGateway gateway;
    private final TransactionTemplate transaction;
    private final IEntitlementService entitlements;
    public VideoGenerationService(JdbcTemplate db, AIModelApiKeyCipher cipher, VideoProviderGateway gateway, PlatformTransactionManager manager,
                                  IEntitlementService entitlements) {
        this.db = db; this.cipher = cipher; this.gateway = gateway; this.transaction = new TransactionTemplate(manager);
        this.entitlements = entitlements;
    }
    private final RowMapper<Task> taskMapper = (r, n) -> new Task(r.getString("id"), r.getString("provider"),
            r.getString("model"), r.getString("prompt"), r.getInt("duration"), r.getString("resolution"),
            r.getString("ratio"), r.getString("status"), r.getString("video_url"), r.getString("error_message"), r.getTimestamp("created_at").toInstant().toString());
    private final RowMapper<Config> configMapper = (r, n) -> new Config(r.getLong("id"),
            new Provider(r.getString("provider"), r.getString("name"), r.getString("model"), r.getBoolean("enabled"),
                    r.getString("api_key_ciphertext") != null, r.getInt("daily_limit")), r.getString("api_key_ciphertext"));
    private Config config(String provider, boolean lock) {
        List<Config> found = db.query("SELECT * FROM ai_video_provider WHERE provider=?" + (lock ? " FOR UPDATE" : ""), configMapper, provider);
        if (found.isEmpty()) throw new IllegalArgumentException("视频供应商不存在");
        return found.get(0);
    }
    public List<Provider> providers(boolean admin) {
        return db.query("SELECT * FROM ai_video_provider ORDER BY id", configMapper).stream()
                .map(Config::view).filter(p -> admin || (p.enabled() && p.apiKeyConfigured())).toList();
    }
    public void save(String provider, Settings settings) {
        transaction.executeWithoutResult(status -> {
            Config current = config(provider, true);
            String encrypted = current.ciphertext();
            if (settings.apiKey() != null && !settings.apiKey().isBlank())
                encrypted = cipher.encrypt(-current.id(), settings.apiKey().trim());
            if (settings.enabled()) {
                if (encrypted == null) throw new IllegalArgumentException("启用前请先配置 API Key");
                cipher.decrypt(-current.id(), encrypted);
            }
            db.update("UPDATE ai_video_provider SET name=?,model=?,enabled=?,api_key_ciphertext=?,daily_limit=? WHERE provider=?",
                    settings.name().trim(), settings.model(), settings.enabled(), encrypted, settings.dailyLimit(), provider);
        });
    }
    public List<Task> list(String owner) {
        return owner == null ? db.query("SELECT * FROM ai_video_task ORDER BY created_at DESC LIMIT 100", taskMapper)
                : db.query("SELECT * FROM ai_video_task WHERE owner_id=? ORDER BY created_at DESC LIMIT 50", taskMapper, owner);
    }
    private Task task(String owner, String id) {
        List<Task> result = db.query("SELECT * FROM ai_video_task WHERE id=? AND owner_id=?", taskMapper, id, owner);
        if (result.isEmpty()) throw new IllegalArgumentException("任务不存在或无访问权限");
        return result.get(0);
    }
    static void validate(Create request) {
        if (request == null || !List.of("seedance", "minimax").contains(request.provider()))
            throw new IllegalArgumentException("不支持的视频供应商");
        if ("seedance".equals(request.provider()) && request.duration() != 5 && request.duration() != 10)
            throw new IllegalArgumentException("Seedance 首版支持 5 秒或 10 秒");
        if ("minimax".equals(request.provider()) && (request.duration() != 6 && request.duration() != 10
                || !"16:9".equals(request.ratio()) || request.duration() == 10 && "1080P".equals(request.resolution())))
            throw new IllegalArgumentException("MiniMax 支持 6 秒或 10 秒；1080P 仅支持 6 秒；文生视频使用默认横屏比例");
    }
    public Task create(String owner, Create request) {
        validate(request);
        boolean unlimited = entitlements.isUnlimited(Long.valueOf(owner));
        String id = UUID.randomUUID().toString();
        // Serialize quota checks per provider across all application instances.
        Config reserved = transaction.execute(status -> {
            Config c = config(request.provider(), true);
            List<Task> duplicate = db.query("SELECT * FROM ai_video_task WHERE owner_id=? AND request_id=?", taskMapper, owner, request.requestId());
            if (!duplicate.isEmpty()) return null;
            if (!c.view().enabled() || c.ciphertext() == null) throw new IllegalArgumentException("该视频模型尚未启用或未配置密钥");
            cipher.decrypt(-c.id(), c.ciphertext());
            Integer used = db.queryForObject("SELECT COUNT(*) FROM ai_video_task WHERE provider=? AND created_at >= CURRENT_DATE", Integer.class, request.provider());
            if (!unlimited && used != null && used >= c.view().dailyLimit()) throw new IllegalArgumentException("该供应商今日调用次数已达上限");
            Integer active = db.queryForObject("SELECT COUNT(*) FROM ai_video_task WHERE owner_id=? AND status IN ('submitting','queued','running','unknown') AND created_at >= CURRENT_DATE", Integer.class, owner);
            if (!unlimited && active != null && active >= 3) throw new IllegalArgumentException("同时最多处理 3 个任务，请等待已有任务完成");
            entitlements.reserve(Long.valueOf(owner), "AI_VIDEO_SECONDS", request.duration(), "ai-video:" + id);
            db.update("INSERT INTO ai_video_task (id,owner_id,request_id,provider,model,prompt,duration,resolution,ratio,status,quota_biz_key) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    id, owner, request.requestId(), request.provider(), c.view().model(), request.prompt().trim(), request.duration(), request.resolution(), request.ratio(), "submitting", "ai-video:" + id);
            return c;
        });
        if (reserved == null) {
            Task previous = db.queryForObject("SELECT * FROM ai_video_task WHERE owner_id=? AND request_id=?", taskMapper, owner, request.requestId());
            if (previous == null || !previous.provider().equals(request.provider()) || !previous.prompt().equals(request.prompt().trim())
                    || previous.duration() != request.duration() || !previous.resolution().equals(request.resolution()) || !previous.ratio().equals(request.ratio()))
                throw new IllegalArgumentException("请求编号已用于其他任务，请刷新后重试");
            return previous;
        }
        try {
            String providerId = gateway.submit(request.provider(), reserved.view().model(), cipher.decrypt(-reserved.id(), reserved.ciphertext()), request);
            db.update("UPDATE ai_video_task SET provider_task_id=?,status='queued',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='submitting'", providerId, id);
        } catch (VideoProviderGateway.SubmissionRejectedException ex) {
            transaction.executeWithoutResult(status -> {
                db.update("UPDATE ai_video_task SET status='failed',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                        "供应商拒绝生成，已退还额度，请检查提示词或联系管理员", id);
                entitlements.release(Long.valueOf(owner), "ai-video:" + id);
            });
        } catch (RuntimeException ex) {
            // A failed transport can still mean the upstream accepted and charged the request.
            db.update("UPDATE ai_video_task SET status='unknown',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='submitting'",
                    "提交结果待核查，请勿重复提交；请管理员在供应商控制台确认任务", id);
        }
        return task(owner, id);
    }
    public Task refresh(String owner, String id) {
        Task current = task(owner, id);
        if (List.of("failed", "unknown", "submitting").contains(current.status())) return current;
        // A completed task can be explicitly refreshed to renew an expiring download URL.
        Config c = config(current.provider(), false);
        String providerId = db.queryForObject("SELECT provider_task_id FROM ai_video_task WHERE id=? AND owner_id=?", String.class, id, owner);
        VideoProviderGateway.Progress progress = gateway.query(current.provider(), cipher.decrypt(-c.id(), c.ciphertext()), providerId);
        transaction.executeWithoutResult(status -> {
            Task locked = db.queryForObject("SELECT * FROM ai_video_task WHERE id=? AND owner_id=? FOR UPDATE", taskMapper, id, owner);
            if (locked == null || "failed".equals(locked.status())) return;
            // Terminal states are monotonic: an older poll cannot undo a completed task.
            if ("succeeded".equals(locked.status()) && !"succeeded".equals(progress.status())) return;
            String quotaKey = db.queryForObject("SELECT quota_biz_key FROM ai_video_task WHERE id=? AND owner_id=?", String.class, id, owner);
            if (quotaKey != null && "failed".equals(progress.status())) entitlements.release(Long.valueOf(owner), quotaKey);
            else if (quotaKey != null && "succeeded".equals(progress.status())) {
                // Duration is restricted and sent by this server; prefer provider-reported actual seconds.
                int seconds = progress.durationSeconds() == null ? current.duration() : progress.durationSeconds();
                entitlements.resize(Long.valueOf(owner), quotaKey, seconds);
                entitlements.commit(Long.valueOf(owner), quotaKey);
            }
            db.update("UPDATE ai_video_task SET status=?,video_url=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?",
                    progress.status(), progress.url(), progress.error(), id, owner);
        });
        return task(owner, id);
    }
}
