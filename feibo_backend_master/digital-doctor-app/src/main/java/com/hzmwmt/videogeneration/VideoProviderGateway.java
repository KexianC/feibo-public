package com.hzmwmt.videogeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Only official fixed origins receive credentials. POST is never retried. */
@Component
public class VideoProviderGateway {
    private final HttpClient client;
    private final ObjectMapper json;
    public VideoProviderGateway() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), new ObjectMapper());
    }
    VideoProviderGateway(HttpClient client, ObjectMapper json) { this.client = client; this.json = json; }
    private String origin(String provider) {
        return switch (provider) {
            case "seedance" -> "https://ark.cn-beijing.volces.com/api/v3";
            case "minimax" -> "https://api.minimax.cn/v1";
            default -> throw new IllegalArgumentException("不支持的视频供应商");
        };
    }
    static Map<String, Object> payload(String provider, String model, VideoGenerationService.Create request) {
        if ("seedance".equals(provider)) return Map.of("model", model,
                "content", List.of(Map.of("type", "text", "text", request.prompt())),
                "duration", request.duration(), "resolution", request.resolution().toLowerCase(), "ratio", request.ratio());
        return Map.of("model", model, "prompt", request.prompt(), "duration", request.duration(),
                "resolution", request.resolution());
    }
    public String submit(String provider, String model, String key, VideoGenerationService.Create request) {
        JsonNode response = call(provider, key, "seedance".equals(provider) ? "/contents/generations/tasks" : "/video_generation",
                payload(provider, model, request));
        String id = response.path("seedance".equals(provider) ? "id" : "task_id").asText();
        if (!id.matches("[A-Za-z0-9_-]{1,160}")) throw new IllegalStateException("供应商未返回有效任务编号，请勿重复提交，联系管理员核查");
        return id;
    }
    public record Progress(String status, String url, String error, Integer durationSeconds) {
        public Progress(String status, String url, String error) { this(status, url, error, null); }
    }
    /** Only explicit upstream rejection is safe to refund; transport uncertainty keeps its hold. */
    static class SubmissionRejectedException extends IllegalStateException {
        SubmissionRejectedException(String message) { super(message); }
    }
    public Progress query(String provider, String key, String taskId) {
        if (!taskId.matches("[A-Za-z0-9_-]{1,160}")) throw new IllegalArgumentException("任务编号无效");
        JsonNode result = call(provider, key, "seedance".equals(provider)
                ? "/contents/generations/tasks/" + taskId : "/query/video_generation?task_id=" + taskId, null);
        String status = result.path("status").asText();
        if (List.of("failed", "expired", "cancelled", "Fail").contains(status))
            return new Progress("failed", null, "供应商生成失败，请检查提示词或联系管理员");
        if (List.of("succeeded", "Success").contains(status)) {
            String url;
            if ("seedance".equals(provider)) url = result.path("content").path("video_url").asText();
            else {
                String file = result.path("file_id").asText();
                if (!file.matches("[0-9]{1,40}")) throw new IllegalStateException("视频文件尚未就绪，请稍后刷新");
                url = call(provider, key, "/files/retrieve?file_id=" + file, null).path("file").path("download_url").asText();
            }
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalStateException("供应商返回的视频地址无效");
            double duration = result.path("duration").asDouble(0);
            Integer seconds = duration > 0 && duration <= 86400 ? (int) Math.ceil(duration) : null;
            return new Progress("succeeded", url, null, seconds);
        }
        if (!List.of("queued", "running", "Preparing", "Queueing", "Processing").contains(status))
            throw new IllegalStateException("供应商返回未知任务状态，请稍后刷新");
        return new Progress(List.of("queued", "Queueing", "Preparing").contains(status) ? "queued" : "running", null, null);
    }
    private JsonNode call(String provider, String key, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin(provider) + path))
                    .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json");
            if (body == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 408)
                throw new SubmissionRejectedException("供应商拒绝请求，请检查权限、余额和模型配置");
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("供应商请求失败（HTTP " + response.statusCode() + "），请管理员检查权限、余额和模型配置");
            JsonNode result = json.readTree(response.body());
            if ((body != null && result.hasNonNull("error")) || result.path("base_resp").path("status_code").asInt(0) != 0)
                throw new SubmissionRejectedException("供应商拒绝请求，请管理员检查余额、模型权限及提示词");
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("供应商请求中断，请稍后核查任务");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("供应商连接超时或响应异常，请勿重复提交，联系管理员核查");
        }
    }
}
