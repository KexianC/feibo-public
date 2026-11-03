package com.hzmwmt.geo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.service.GeoEngineClient;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OpenAiCompatibleGeoEngineClient implements GeoEngineClient {

    private static final String PROTOCOL_DASHSCOPE = "dashscope";
    private static final String PROTOCOL_QIANFAN_AI_SEARCH = "qianfan-ai-search";
    private static final String DEFAULT_DASHSCOPE_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    private static final String DEFAULT_QIANFAN_AI_SEARCH_ENDPOINT =
            "https://qianfan.baidubce.com/v2/ai_search/web_search";
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(?:ref_)?(\\d+)]");

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public GeoEngineAnswer ask(GeoProperties.EngineProperties engine, String queryText) {
        validate(engine);
        if (PROTOCOL_DASHSCOPE.equalsIgnoreCase(engine.getProtocol())) {
            return askDashScope(engine, queryText);
        }
        if (PROTOCOL_QIANFAN_AI_SEARCH.equalsIgnoreCase(engine.getProtocol())) {
            return askQianfanAiSearch(engine, queryText);
        }
        return askOpenAiCompatible(engine, queryText);
    }

    @Override
    public GeoEngineAnswer diagnose(GeoProperties.EngineProperties engine, String diagnosticPrompt) {
        validate(engine);
        if (PROTOCOL_QIANFAN_AI_SEARCH.equalsIgnoreCase(engine.getProtocol())) {
            return askQianfanAiSearch(engine, diagnosticPrompt);
        }
        if (PROTOCOL_DASHSCOPE.equalsIgnoreCase(engine.getProtocol())) {
            return diagnoseDashScope(engine, diagnosticPrompt);
        }
        return diagnoseOpenAiCompatible(engine, diagnosticPrompt);
    }

    private GeoEngineAnswer diagnoseDashScope(GeoProperties.EngineProperties engine, String diagnosticPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", engine.getModel());
        request.put("input", Map.of("messages", List.of(
                Map.of("role", "system", "content", diagnosticSystemPrompt()),
                Map.of("role", "user", "content", diagnosticPrompt)
        )));
        request.put("parameters", Map.of(
                "result_format", "message",
                "temperature", 0.1,
                "enable_search", false
        ));
        JsonNode response = postJson(dashScopeEndpoint(engine.getBaseUrl()), engine, request);
        String answer = response == null ? "" : response.at("/output/choices/0/message/content").asText("");
        return new GeoEngineAnswer(engine.getName(), answer);
    }

    private GeoEngineAnswer diagnoseOpenAiCompatible(GeoProperties.EngineProperties engine, String diagnosticPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", engine.getModel());
        request.put("messages", List.of(
                Map.of("role", "system", "content", diagnosticSystemPrompt()),
                Map.of("role", "user", "content", diagnosticPrompt)
        ));
        request.put("temperature", 0.1);
        JsonNode response = postJson(openAiCompatibleEndpoint(engine.getBaseUrl()), engine, request);
        String answer = response == null ? "" : response.at("/choices/0/message/content").asText("");
        return new GeoEngineAnswer(engine.getName(), answer);
    }

    private GeoEngineAnswer askDashScope(GeoProperties.EngineProperties engine, String queryText) {
        Map<String, Object> searchOptions = new LinkedHashMap<>();
        if (StringUtils.hasText(engine.getSearchStrategy())) {
            searchOptions.put("search_strategy", engine.getSearchStrategy());
        }
        if (engine.isEnableSource()) {
            searchOptions.put("enable_source", true);
        }
        if (engine.isEnableCitation()) {
            searchOptions.put("enable_citation", true);
            searchOptions.put("citation_format", "[<number>]");
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("result_format", "message");
        parameters.put("temperature", engine.getTemperature());
        parameters.put("enable_search", engine.isEnableSearch());
        if (!searchOptions.isEmpty()) {
            parameters.put("search_options", searchOptions);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", engine.getModel());
        request.put("input", Map.of("messages", List.of(
                Map.of("role", "system", "content", monitorSystemPrompt()),
                Map.of("role", "user", "content", buildMonitorPrompt(queryText))
        )));
        request.put("parameters", parameters);

        JsonNode response = postJson(dashScopeEndpoint(engine.getBaseUrl()), engine, request);
        JsonNode contentNode = response == null ? null : response.at("/output/choices/0/message/content");
        String answer = contentNode == null || contentNode.isMissingNode() ? "" : contentNode.asText("");
        JsonNode searchInfo = response == null ? null : response.at("/output/search_info");
        List<String> citedUrls = extractDashScopeCitedUrls(answer, searchInfo);
        return new GeoEngineAnswer(engine.getName(), answer, citedUrls, toJson(searchInfo));
    }

    private GeoEngineAnswer askQianfanAiSearch(GeoProperties.EngineProperties engine, String queryText) {
        int topK = Math.max(1, Math.min(engine.getSearchTopK(), 50));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", List.of(Map.of(
                "role", "user",
                "content", fitBaiduQuery(queryText)
        )));
        request.put("search_source", "baidu_search_v2");
        request.put("resource_type_filter", List.of(Map.of("type", "web", "top_k", topK)));
        request.put("safe_search", engine.isSafeSearch());
        if (StringUtils.hasText(engine.getSearchRecencyFilter())) {
            request.put("search_recency_filter", engine.getSearchRecencyFilter());
        }
        if (StringUtils.hasText(engine.getSearchStrategy()) && !"max".equalsIgnoreCase(engine.getSearchStrategy())) {
            request.put("sort", Map.of("priority", engine.getSearchStrategy()));
        }

        JsonNode response = postQianfanJson(qianfanAiSearchEndpoint(engine.getBaseUrl()), engine, request);
        JsonNode references = response == null ? null : response.path("references");
        List<String> citedUrls = new ArrayList<>();
        List<Map<String, Object>> sourceDetails = new ArrayList<>();
        StringBuilder answer = new StringBuilder("百度千帆 AI Search 返回的搜索结果摘要：");
        if (references != null && references.isArray()) {
            for (JsonNode ref : references) {
                String url = ref.path("url").asText("");
                int rank = ref.path("id").asInt(sourceDetails.size() + 1);
                String title = ref.path("title").asText("");
                String snippet = ref.path("snippet").asText(ref.path("content").asText(""));
                if (StringUtils.hasText(url)) {
                    citedUrls.add(url);
                }
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("rank", rank);
                source.put("title", title);
                source.put("url", url);
                source.put("website", ref.path("website").asText(""));
                source.put("webAnchor", ref.path("web_anchor").asText(""));
                source.put("snippet", snippet);
                source.put("date", ref.path("date").asText(""));
                source.put("type", ref.path("type").asText(""));
                sourceDetails.add(source);
                answer.append("\n[").append(rank).append("] ")
                        .append(title);
                if (StringUtils.hasText(snippet)) {
                    answer.append(" - ").append(snippet);
                }
                if (StringUtils.hasText(url)) {
                    answer.append("\n来源：").append(url);
                }
            }
        }
        GeoEngineAnswer result = new GeoEngineAnswer(engine.getName(), answer.toString(), citedUrls, toJson(response));
        result.setSourceCount(sourceDetails.size());
        result.setRankPosition(sourceDetails.isEmpty() ? null : 1);
        result.setSourceDetails(toJsonObject(sourceDetails));
        return result;
    }

    private GeoEngineAnswer askOpenAiCompatible(GeoProperties.EngineProperties engine, String queryText) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", engine.getModel());
        request.put("messages", List.of(
                Map.of("role", "system", "content", monitorSystemPrompt()),
                Map.of("role", "user", "content", buildMonitorPrompt(queryText))
        ));
        request.put("temperature", engine.getTemperature());
        if (engine.isEnableSearch()) {
            request.put("enable_search", true);
            if (StringUtils.hasText(engine.getSearchStrategy())) {
                request.put("search_options", Map.of("search_strategy", engine.getSearchStrategy()));
            }
        }

        JsonNode response = postJson(openAiCompatibleEndpoint(engine.getBaseUrl()), engine, request);
        JsonNode contentNode = response == null ? null : response.at("/choices/0/message/content");
        String answer = contentNode == null || contentNode.isMissingNode() ? "" : contentNode.asText("");
        return new GeoEngineAnswer(engine.getName(), answer);
    }

    private JsonNode postJson(String url, GeoProperties.EngineProperties engine, Map<String, Object> request) {
        WebClient.Builder builder = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(engine.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + engine.getApiKey());
        }
        return builder.build()
                .post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(Math.max(5, engine.getTimeoutSeconds())));
    }

    private JsonNode postQianfanJson(String url, GeoProperties.EngineProperties engine, Map<String, Object> request) {
        WebClient.Builder builder = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + engine.getApiKey())
                .defaultHeader("X-Appbuilder-Authorization", "Bearer " + engine.getApiKey());
        return builder.build()
                .post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(Math.max(5, engine.getTimeoutSeconds())));
    }

    private void validate(GeoProperties.EngineProperties engine) {
        if (engine == null) {
            throw new AppException("GEO_ENGINE_CONFIG_INVALID", "GEO monitor engine is missing");
        }
        if (!StringUtils.hasText(engine.getApiKey())) {
            throw new AppException("GEO_ENGINE_CONFIG_INVALID", "GEO monitor engine API key is missing");
        }
        if (PROTOCOL_QIANFAN_AI_SEARCH.equalsIgnoreCase(engine.getProtocol())) {
            return;
        }
        if (!StringUtils.hasText(engine.getModel())) {
            throw new AppException("GEO_ENGINE_CONFIG_INVALID", "GEO monitor engine model is missing");
        }
        if (!PROTOCOL_DASHSCOPE.equalsIgnoreCase(engine.getProtocol()) && !StringUtils.hasText(engine.getBaseUrl())) {
            throw new AppException("GEO_ENGINE_CONFIG_INVALID", "GEO monitor engine endpoint is missing");
        }
    }

    private String dashScopeEndpoint(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_DASHSCOPE_ENDPOINT;
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/services/aigc/text-generation/generation")) {
            return trimmed;
        }
        String normalized = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (normalized.endsWith("/api/v1")) {
            return normalized + "/services/aigc/text-generation/generation";
        }
        return normalized;
    }

    private String qianfanAiSearchEndpoint(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_QIANFAN_AI_SEARCH_ENDPOINT;
        }
        return baseUrl.trim();
    }

    private String fitBaiduQuery(String queryText) {
        String value = StringUtils.hasText(queryText) ? queryText.trim() : "";
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int charWidth = codePoint <= 0x7F ? 1 : 2;
            if (width + charWidth > 72) {
                break;
            }
            sb.appendCodePoint(codePoint);
            width += charWidth;
            offset += Character.charCount(codePoint);
        }
        return sb.toString();
    }

    private String openAiCompatibleEndpoint(String baseUrl) {
        String trimmed = baseUrl.trim();
        String normalized = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return normalized.endsWith(CHAT_COMPLETIONS_SUFFIX) ? normalized : normalized + CHAT_COMPLETIONS_SUFFIX;
    }

    private List<String> extractDashScopeCitedUrls(String answer, JsonNode searchInfo) {
        if (searchInfo == null || searchInfo.isMissingNode()) {
            return List.of();
        }
        JsonNode results = searchInfo.path("search_results");
        if (!results.isArray() || results.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> urlByIndex = new LinkedHashMap<>();
        int fallbackIndex = 1;
        for (JsonNode result : results) {
            String url = result.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                fallbackIndex++;
                continue;
            }
            int index = result.path("index").asInt(fallbackIndex);
            urlByIndex.put(index, url);
            fallbackIndex++;
        }

        Set<Integer> citedIndices = extractCitationIndices(answer);
        Set<String> urls = new LinkedHashSet<>();
        if (!citedIndices.isEmpty()) {
            citedIndices.forEach(index -> {
                String url = urlByIndex.get(index);
                if (StringUtils.hasText(url)) {
                    urls.add(url);
                }
            });
        }
        if (urls.isEmpty()) {
            urls.addAll(urlByIndex.values());
        }
        return new ArrayList<>(urls);
    }

    private Set<Integer> extractCitationIndices(String answer) {
        Set<Integer> indices = new LinkedHashSet<>();
        if (!StringUtils.hasText(answer)) {
            return indices;
        }
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                indices.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed citation markers.
            }
        }
        return indices;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonObject(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String monitorSystemPrompt() {
        return "You are an objective GEO monitoring assistant for healthcare content. "
                + "Use web search when available, cite sources when available, and keep medical answers educational only.";
    }

    private String diagnosticSystemPrompt() {
        return "You are an internal GEO observability analyst. Diagnose only from the supplied evidence. "
                + "Do not claim that conversations train or influence a public model, do not recommend manipulation, "
                + "and return strict JSON without markdown.";
    }

    private String buildMonitorPrompt(String queryText) {
        return """
                请像真实用户使用 AI 搜索一样回答下面的问题。
                要求：
                1. 如果答案引用了网页、平台内容或公开资料，请保留来源链接或引用角标。
                2. 不要迎合提问中的品牌、医院、科室或医生名称，按你能检索或理解到的信息客观回答。
                3. 医疗健康内容必须提示仅供科普参考，不能替代线下诊疗。

                问题：
                %s
                """.formatted(StringUtils.hasText(queryText) ? queryText : "");
    }
}
