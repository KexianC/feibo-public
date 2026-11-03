package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoFeedbackSuggestionMapper;
import com.hzmwmt.geo.mapper.GeoMonitorRecordMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.dto.GeoFeedbackGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.GeoMonitorScheduler;
import com.hzmwmt.geo.service.GeoOptimizationAssistant;
import com.hzmwmt.geo.service.GeoOptimizationService;
import com.hzmwmt.geo.service.GeoSiteRecommendationService;
import com.hzmwmt.geo.service.MedicalComplianceService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeoOptimizationServiceImpl implements GeoOptimizationService {

    @Resource
    private GeoFeedbackSuggestionMapper feedbackSuggestionMapper;

    @Resource
    private GeoMonitorRecordMapper monitorRecordMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoOptimizationAssistant geoOptimizationAssistant;

    @Resource
    private MedicalComplianceService medicalComplianceService;

    @Resource
    private GeoMonitorScheduler geoMonitorScheduler;

    @Resource
    private GeoSiteRecommendationService siteRecommendationService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional
    public List<GeoFeedbackSuggestion> generateSuggestions(GeoFeedbackGenerateRequest request) {
        List<GeoMonitorRecord> missedRecords = resolveMissedRecords(request);
        List<GeoFeedbackSuggestion> suggestions = new ArrayList<>();
        for (GeoMonitorRecord record : missedRecords) {
            suggestions.addAll(generateForMonitorRecord(record));
        }
        return suggestions;
    }

    @Override
    @Transactional
    public List<GeoFeedbackSuggestion> generateForMonitorRecord(GeoMonitorRecord record) {
        if (record == null || record.getId() == null) {
            return List.of();
        }
        List<GeoFeedbackSuggestion> existing = feedbackSuggestionMapper.selectList(new LambdaQueryWrapper<GeoFeedbackSuggestion>()
                .eq(GeoFeedbackSuggestion::getMonitorRecordId, record.getId())
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return existing;
        }
        GeoQuery query = geoQueryMapper.selectById(record.getQueryId());
        if (query == null) {
            return List.of();
        }
        GeoFeedbackSuggestion suggestion = buildSuggestion(query, record);
        feedbackSuggestionMapper.insert(suggestion);
        siteRecommendationService.createFromMonitor(query, record, suggestion);
        List<GeoMonitorTask> retests = geoMonitorScheduler.scheduleRetests(record, query, suggestion.getId());
        if (!retests.isEmpty()) {
            GeoFeedbackSuggestion update = new GeoFeedbackSuggestion();
            update.setId(suggestion.getId());
            update.setRetestStatus(1);
            update.setRetestPlan(toJson(retests.stream().map(GeoMonitorTask::getPlannedTime).toList()));
            feedbackSuggestionMapper.updateById(update);
            suggestion.setRetestStatus(1);
            suggestion.setRetestPlan(update.getRetestPlan());
        }
        return List.of(suggestion);
    }

    private List<GeoMonitorRecord> resolveMissedRecords(GeoFeedbackGenerateRequest request) {
        if (request != null && request.getMonitorRecordId() != null) {
            GeoMonitorRecord record = monitorRecordMapper.selectById(request.getMonitorRecordId());
            return record == null ? List.of() : List.of(record);
        }
        LambdaQueryWrapper<GeoMonitorRecord> wrapper = new LambdaQueryWrapper<GeoMonitorRecord>()
                .and(w -> w.eq(GeoMonitorRecord::getMentioned, 0)
                        .or().eq(GeoMonitorRecord::getCited, 0)
                        .or().eq(GeoMonitorRecord::getOfficialSourceCount, 0))
                .eq(GeoMonitorRecord::getStatus, 1)
                .orderByDesc(GeoMonitorRecord::getMonitoredTime);
        if (request != null && request.getQueryId() != null) {
            wrapper.eq(GeoMonitorRecord::getQueryId, request.getQueryId());
        }
        wrapper.last("LIMIT " + Math.max(1, request == null || request.getLimit() == null ? 20 : request.getLimit()));
        return monitorRecordMapper.selectList(wrapper);
    }

    private GeoFeedbackSuggestion buildSuggestion(GeoQuery query, GeoMonitorRecord record) {
        String missReason = missReason(record);
        String clusterKey = clusterKey(query);
        String prompt = """
                你是医疗内容GEO优化助手。请根据未命中的AI搜索问题生成补充内容。
                严格要求：
                1. 不得给出个人诊疗建议，不得承诺疗效，不得推荐具体药品。
                2. 内容用于公开科普页面、医院页、科室页或医生页，必须客观、可验证、适合医疗合规审核。
                3. 优先补充“杭州 + 症状/疗法/科室选择”的本地化 FAQ 和结构化摘要。
                4. 输出严格 JSON：
                {
                  "title":"",
                  "supplement":"",
                  "faq":[{"question":"","answer":""}],
                  "structuredSummary":{"about":"","audience":"","localIntent":"","keyPoints":[],"disclaimer":""}
                }

                未命中原因：%s
                GEO问题：%s
                聚类键：%s
                疾病/关键词：%s
                科室：%s
                医生：%s
                地区：%s
                场景：%s
                AI平台：%s
                AI引擎回答摘要：%s
                竞品/第三方提及：%s
                """.formatted(
                missReason,
                query.getQueryText(),
                clusterKey,
                nullToEmpty(query.getDisease()),
                nullToEmpty(query.getDepartmentName()),
                nullToEmpty(query.getDoctorName()),
                nullToEmpty(query.getRegion()),
                nullToEmpty(query.getTreatmentScenario()),
                nullToEmpty(record.getEngine()),
                truncate(record.getAnswerText(), geoProperties.getFeedbackContextLimit()),
                nullToEmpty(record.getCompetitorMentions())
        );

        String response = geoOptimizationAssistant.optimize(prompt).collectList().map(parts -> String.join("", parts)).block();
        return parseSuggestion(query, record, response, missReason, clusterKey);
    }

    private GeoFeedbackSuggestion parseSuggestion(GeoQuery query, GeoMonitorRecord record, String response, String missReason, String clusterKey) {
        String normalized = normalizeJson(response);
        String title = "补充：" + query.getQueryText();
        String content = normalized;
        String faqJson = "[]";
        String structuredSummary = "{}";

        try {
            JsonNode root = objectMapper.readTree(normalized);
            title = root.path("title").asText(title);
            content = root.path("supplement").asText(content);
            faqJson = objectMapper.writeValueAsString(root.path("faq"));
            structuredSummary = objectMapper.writeValueAsString(root.path("structuredSummary"));
        } catch (Exception ignored) {
            // Keep model output as supplemental content when JSON parsing fails.
        }

        if (!StringUtils.hasText(content)) {
            content = "建议补充与“" + query.getQueryText() + "”相关的本地化科普内容、FAQ、科室选择说明和结构化摘要。";
        }
        ComplianceAuditResult auditResult = medicalComplianceService.audit(title, content + "\n" + faqJson + "\n" + structuredSummary);
        Long suggestionId = snowflakeIdGenerator.nextId();
        return GeoFeedbackSuggestion.builder()
                .id(suggestionId)
                .activeRetestBatchId(suggestionId)
                .queryId(query.getId())
                .monitorRecordId(record.getId())
                .articleId(record.getArticleId())
                .suggestionType("closed_loop")
                .title(truncate(title, 255))
                .content(content)
                .faqJson(faqJson)
                .structuredSummary(structuredSummary)
                .missReason(missReason)
                .clusterKey(clusterKey)
                .complianceStatus(auditResult.isPassed() ? 1 : 2)
                .complianceResult(toJson(auditResult))
                .publishStatus(0)
                .targetChannels(toJson(List.of(
                        "external_content_distribution",
                        "official_site_change_request",
                        "manual_indexing_request"
                )))
                .executionMode("official_site_recommendation_only")
                .officialSiteRecommendation(toJson(java.util.Map.of(
                        "mode", "recommendation_only",
                        "owner", "hospital_website_owner",
                        "suggestions", List.of("补充直接回答和FAQ", "补充医疗实体结构化数据", "补充可核验来源和审核信息"),
                        "prohibitedActions", List.of("auto_publish_to_official_site", "auto_submit_official_site")
                )))
                .retestStatus(0)
                .retestPlan("[]")
                .status(0)
                .build();
    }

    private String missReason(GeoMonitorRecord record) {
        List<String> reasons = new ArrayList<>();
        if (record.getMentioned() == null || record.getMentioned() == 0) {
            reasons.add("未提及目标医院/科室/医生");
        }
        if (record.getCited() == null || record.getCited() == 0) {
            reasons.add("未引用公开URL");
        }
        if (record.getOfficialSourceCount() == null || record.getOfficialSourceCount() == 0) {
            reasons.add("缺少官网或官方来源引用");
        }
        if (StringUtils.hasText(record.getCompetitorMentions()) && !"[]".equals(record.getCompetitorMentions())) {
            reasons.add("竞品或第三方内容权重更高");
        }
        return reasons.isEmpty() ? "GEO分偏低，需要补强来源与结构化内容" : String.join("；", reasons);
    }

    private String clusterKey(GeoQuery query) {
        return String.join("|",
                nullToEmpty(query.getRegion()),
                nullToEmpty(query.getDepartmentName()),
                nullToEmpty(query.getDisease()),
                nullToEmpty(query.getTreatmentScenario()),
                nullToEmpty(query.getQueryCluster())
        );
    }

    private String normalizeJson(String response) {
        if (response == null) {
            return "";
        }
        String normalized = response.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        int limit = Math.max(100, maxLength);
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}
