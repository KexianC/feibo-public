package com.hzmwmt.ai.rag.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hzmwmt.ai.rag.config.RagProperties;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.model.entity.QdrantPoint;
import com.hzmwmt.ai.rag.model.entity.QdrantSearchHit;
import com.hzmwmt.ai.rag.service.QdrantRestClient;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class QdrantRestClientImpl implements QdrantRestClient {

    @Resource
    private RagProperties ragProperties;

    /**
     * 已确认的向量维度缓存，避免重复查询集合信息。
     */
    private final AtomicInteger knownVectorSize = new AtomicInteger(-1);

    @Override
    public String collectionName() {
        return ragProperties.getQdrant().getCollectionName();
    }

    @Override
    public void ensureCollection(int vectorSize) {
        int cachedSize = knownVectorSize.get();
        if (cachedSize == vectorSize) {
            return;
        }

        synchronized (this) {
            cachedSize = knownVectorSize.get();
            if (cachedSize == vectorSize) {
                return;
            }

            log.info("检查 Qdrant collection，vectorSize={}", vectorSize);
            Integer existingSize = fetchCollectionVectorSize();
            if (existingSize == null) {
                log.info("Qdrant collection 不存在，准备创建，vectorSize={}", vectorSize);
                createCollection(vectorSize);
                knownVectorSize.set(vectorSize);
                return;
            }

            if (existingSize != vectorSize) {
                log.error("Qdrant collection 维度不匹配，expected={}, actual={}", vectorSize, existingSize);
                throw new AppException("QDRANT_COLLECTION_MISMATCH",
                        "向量集合维度不匹配，期望维度：" + vectorSize + "，实际维度：" + existingSize);
            }

            log.info("Qdrant collection 可复用，vectorSize={}", existingSize);
            knownVectorSize.set(existingSize);
        }
    }

    @Override
    public void upsert(List<QdrantPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }

        List<Map<String, Object>> requestPoints = new ArrayList<>(points.size());
        for (QdrantPoint point : points) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", point.getId());
            item.put("vector", point.getVector());
            item.put("payload", point.getPayload());
            requestPoints.add(item);
        }

        log.info("批量写入 Qdrant 点位，pointCount={}", points.size());
        try {
            client().put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collections/{collectionName}/points")
                            .queryParam("wait", true)
                            .build(collectionName()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("points", requestPoints))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException exception) {
            log.error("Qdrant 点位写入失败，pointCount={}, status={}, exceptionType={}",
                    points.size(),
                    exception.getStatusCode().value(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
        log.info("Qdrant 点位写入完成，pointCount={}", points.size());
    }

    @Override
    public void deleteByFileId(long fileId) {
        log.info("按文档 ID 删除 Qdrant 向量，fileId={}", fileId);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("filter", Map.of(
                "must", List.of(
                        Map.of(
                                "key", "fileId",
                                "match", Map.of("value", fileId)
                        )
                )
        ));

        try {
            client().post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collections/{collectionName}/points/delete")
                            .queryParam("wait", true)
                            .build(collectionName()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException exception) {
            log.error("按文档 ID 删除 Qdrant 向量失败，fileId={}, status={}, exceptionType={}",
                    fileId,
                    exception.getStatusCode().value(),
                    exception.getClass().getSimpleName());
            throw exception;
        }

        log.info("按文档 ID 删除 Qdrant 向量完成，fileId={}", fileId);
    }

    @Override
    public List<QdrantSearchHit> search(KnowledgeAgentScope scope, List<Float> vector, int limit, double scoreThreshold) {
        log.info("执行 Qdrant 检索，limit={}", limit);
        try {
            List<QdrantSearchHit> hits = new ArrayList<>();
            for (Map<String, Object> filter : buildScopeFilters(scope)) {
                hits.addAll(queryPoints(filter, vector, limit, scoreThreshold));
            }
            hits = hits.stream()
                    .collect(LinkedHashMap<String, QdrantSearchHit>::new,
                            (map, hit) -> map.merge(hit.getId(), hit, (left, right) -> left.getScore() >= right.getScore() ? left : right),
                            LinkedHashMap::putAll)
                    .values()
                    .stream()
                    .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                    .limit(limit)
                    .toList();
            log.info("Qdrant query 接口检索成功，hitCount={}", hits.size());
            return hits;
        } catch (WebClientResponseException.NotFound ignored) {
            log.warn("Qdrant collection 不存在，返回空结果，status={}", 404);
            return List.of();
        }
    }

    private List<QdrantSearchHit> queryPoints(Map<String, Object> filter, List<Float> vector, int limit, double scoreThreshold) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", vector);
        request.put("limit", limit);
        request.put("with_payload", true);
        request.put("score_threshold", scoreThreshold);
        if (filter != null && !filter.isEmpty()) {
            request.put("filter", filter);
        }

        // 当前项目按最新版 Qdrant 的 query 接口检索。
        String responseBody = client().post()
                .uri("/collections/{collectionName}/points/query", collectionName())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JSONObject response = parseObject(responseBody);
        JSONObject result = response == null ? null : response.getJSONObject("result");
        JSONArray points = result == null ? null : result.getJSONArray("points");
        return readHits(points);
    }

    private List<Map<String, Object>> buildScopeFilters(KnowledgeAgentScope scope) {
        if (scope == null || scope.getAgentType() == null) {
            return List.of(Map.of());
        }
        List<Map<String, Object>> filters = new ArrayList<>();
        if (scope.getAgentType() == KnowledgeAgentTypeConstants.HOSPITAL && scope.getHospitalId() != null) {
            filters.add(filter(match("hospitalId", scope.getHospitalId())));
            return filters;
        }
        if (scope.getAgentType() == KnowledgeAgentTypeConstants.DEPARTMENT) {
            if (scope.getDepartmentId() != null) {
                filters.add(filter(match("departmentId", scope.getDepartmentId())));
            }
            if (scope.getHospitalId() != null) {
                filters.add(filter(
                        match("agentType", KnowledgeAgentTypeConstants.HOSPITAL),
                        match("hospitalId", scope.getHospitalId())
                ));
            }
            return filters.isEmpty() ? List.of(Map.of()) : filters;
        }
        if (scope.getAgentType() == KnowledgeAgentTypeConstants.DOCTOR) {
            if (scope.getDoctorId() != null) {
                filters.add(filter(match("doctorId", scope.getDoctorId())));
            }
            if (scope.getDepartmentId() != null) {
                filters.add(filter(
                        match("agentType", KnowledgeAgentTypeConstants.DEPARTMENT),
                        match("departmentId", scope.getDepartmentId())
                ));
            }
            if (scope.getHospitalId() != null) {
                filters.add(filter(
                        match("agentType", KnowledgeAgentTypeConstants.HOSPITAL),
                        match("hospitalId", scope.getHospitalId())
                ));
            }
            return filters.isEmpty() ? List.of(Map.of()) : filters;
        }
        return List.of(Map.of());
    }

    @SafeVarargs
    private Map<String, Object> filter(Map<String, Object>... mustConditions) {
        return Map.of("must", List.of(mustConditions));
    }

    private Map<String, Object> match(String key, Object value) {
        return Map.of(
                "key", key,
                "match", Map.of("value", value)
        );
    }

    private Integer fetchCollectionVectorSize() {
        try {
            String responseBody = client().get()
                    .uri("/collections/{collectionName}", collectionName())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 当前项目只按最新版 Qdrant 的单向量结构读取 size。
            JSONObject response = parseObject(responseBody);
            JSONObject result = response == null ? null : response.getJSONObject("result");
            JSONObject config = result == null ? null : result.getJSONObject("config");
            JSONObject params = config == null ? null : config.getJSONObject("params");
            JSONObject vectors = params == null ? null : params.getJSONObject("vectors");
            if (vectors == null) {
                return null;
            }
            return vectors.getInt("size");
        } catch (WebClientResponseException.NotFound ignored) {
            return null;
        }
    }

    private void createCollection(int vectorSize) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", vectorSize);
        vectors.put("distance", ragProperties.getQdrant().getDistance());

        try {
            client().put()
                    .uri("/collections/{collectionName}", collectionName())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("vectors", vectors))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("创建 Qdrant collection 成功，vectorSize={}", vectorSize);
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().value() == 409 || containsAlreadyExists(exception.getResponseBodyAsString())) {
                log.info("Qdrant collection 已存在，跳过创建");
                return;
            }
            log.error("创建 Qdrant collection 失败，vectorSize={}, status={}, exceptionType={}",
                    vectorSize, exception.getStatusCode().value(), exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private List<QdrantSearchHit> readHits(JSONArray pointsArray) {
        if (pointsArray == null || pointsArray.isEmpty()) {
            return List.of();
        }

        List<QdrantSearchHit> hits = new ArrayList<>();
        for (Object item : pointsArray) {
            if (!(item instanceof JSONObject node)) {
                continue;
            }
            Object idValue = node.get("id");
            String id = idValue == null ? null : String.valueOf(idValue);
            double score = node.getDouble("score", 0D);

            Map<String, Object> payload = new LinkedHashMap<>();
            JSONObject payloadObject = node.getJSONObject("payload");
            if (payloadObject != null) {
                // payload 统一摊平成基础类型或 JSON 字符串，方便业务层直接消费。
                for (Map.Entry<String, Object> entry : payloadObject.entrySet()) {
                    payload.put(entry.getKey(), readNodeValue(entry.getValue()));
                }
            }
            hits.add(new QdrantSearchHit(id, score, payload));
        }
        return hits;
    }

    private Object readNodeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return JSONUtil.toJsonStr(value);
        }
        return value;
    }

    private boolean containsAlreadyExists(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        String normalized = responseBody.toLowerCase();
        return normalized.contains("already exists")
                || normalized.contains("collection") && normalized.contains("exists");
    }

    private JSONObject parseObject(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        return JSONUtil.parseObj(responseBody);
    }

    private WebClient client() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(ragProperties.getQdrant().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(ragProperties.getQdrant().getApiKey())) {
            builder.defaultHeader("api-key", ragProperties.getQdrant().getApiKey());
        }
        return builder.build();
    }
}
