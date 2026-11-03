package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.mapper.GeoSiteRecommendationMapper;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.dto.GeoSiteRecommendationStatusRequest;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSiteRecommendation;
import com.hzmwmt.geo.service.GeoSiteRecommendationService;
import com.hzmwmt.geo.service.MedicalComplianceService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeoSiteRecommendationServiceImpl implements GeoSiteRecommendationService {

    private static final String RECOMMENDATION_ONLY = "recommendation_only";

    @Resource
    private GeoSiteRecommendationMapper siteRecommendationMapper;

    @Resource
    private MedicalComplianceService medicalComplianceService;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public GeoSiteRecommendation createFromMonitor(GeoQuery query,
                                                   GeoMonitorRecord record,
                                                   GeoFeedbackSuggestion suggestion) {
        if (query == null || record == null || record.getId() == null || suggestion == null) {
            return null;
        }
        GeoSiteRecommendation existing = siteRecommendationMapper.selectOne(new LambdaQueryWrapper<GeoSiteRecommendation>()
                .eq(GeoSiteRecommendation::getMonitorRecordId, record.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        String type = recommendationType(record.getMentioned(), record.getCited(), record.getOfficialSourceCount());
        String recommendedChange = buildMonitorChange(query, record, suggestion);
        return insert(query, null, record.getId(), type, suggestion.getMissReason(), recommendedChange,
                contentBrief(query, type, suggestion.getFaqJson(), suggestion.getStructuredSummary()),
                Map.of("monitorRecordId", record.getId(), "engine", nullToEmpty(record.getEngine()),
                        "visibilityScore", safeInt(record.getVisibilityScore()), "citedUrls", nullToEmpty(record.getCitedUrls())),
                query.getPriority());
    }

    @Override
    public GeoSiteRecommendation createFromProbe(GeoQuery query,
                                                 GeoDialogueProbe probe,
                                                 GeoGapAnalysis analysis) {
        if (query == null || probe == null || probe.getId() == null || analysis == null) {
            return null;
        }
        GeoSiteRecommendation existing = siteRecommendationMapper.selectOne(new LambdaQueryWrapper<GeoSiteRecommendation>()
                .eq(GeoSiteRecommendation::getProbeId, probe.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        String type = recommendationType(probe.getMentioned(), probe.getCited(), probe.getOfficialSourceCount());
        String recommendedChange = buildProbeChange(query, analysis);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("probeId", probe.getId());
        evidence.put("engine", probe.getEngine());
        evidence.put("probeMode", probe.getProbeMode());
        evidence.put("visibilityScore", safeInt(probe.getVisibilityScore()));
        evidence.put("sourceRequirements", analysis.getSourceRequirements());
        evidence.put("baselineSources", probe.getBaselineSources());
        return insert(query, probe.getId(), null, type, analysis.getGapSummary(), recommendedChange,
                contentBrief(query, type, "[]", "{}"), evidence, query.getPriority());
    }

    @Override
    public IPage<GeoSiteRecommendation> page(Page<GeoSiteRecommendation> page,
                                             Integer status,
                                             String entityType,
                                             String recommendationType) {
        LambdaQueryWrapper<GeoSiteRecommendation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, GeoSiteRecommendation::getStatus, status);
        wrapper.eq(StringUtils.hasText(entityType), GeoSiteRecommendation::getEntityType, entityType);
        wrapper.eq(StringUtils.hasText(recommendationType), GeoSiteRecommendation::getRecommendationType, recommendationType);
        wrapper.orderByDesc(GeoSiteRecommendation::getPriority)
                .orderByDesc(GeoSiteRecommendation::getCreatedTime);
        return siteRecommendationMapper.selectPage(page, wrapper);
    }

    @Override
    public GeoSiteRecommendation updateStatus(Long id, GeoSiteRecommendationStatusRequest request) {
        GeoSiteRecommendation existing = siteRecommendationMapper.selectById(id);
        if (existing == null) {
            throw new AppException("GEO_SITE_RECOMMENDATION_NOT_FOUND", "官网修改建议不存在", HttpStatus.NOT_FOUND);
        }
        if (request == null || request.getStatus() == null || request.getStatus() < 0 || request.getStatus() > 4) {
            throw new AppException("PARAM_INVALID", "官网建议状态不合法");
        }
        GeoSiteRecommendation update = new GeoSiteRecommendation();
        update.setId(id);
        update.setStatus(request.getStatus());
        update.setStatusNote(truncate(request.getStatusNote(), 1000));
        if (request.getStatus() == 1 && existing.getSubmittedTime() == null) {
            update.setSubmittedTime(LocalDateTime.now());
        }
        if (request.getStatus() == 4) {
            update.setCompletedTime(LocalDateTime.now());
        }
        siteRecommendationMapper.updateById(update);
        return siteRecommendationMapper.selectById(id);
    }

    private GeoSiteRecommendation insert(GeoQuery query,
                                         Long probeId,
                                         Long monitorRecordId,
                                         String type,
                                         String gap,
                                         String recommendedChange,
                                         Map<String, Object> brief,
                                         Map<String, Object> evidence,
                                         Integer priority) {
        EntityScope scope = entityScope(query);
        String title = scope.name() + " - " + recommendationTitle(type);
        ComplianceAuditResult audit = medicalComplianceService.audit(title, recommendedChange + "\n" + toJson(brief));
        GeoSiteRecommendation recommendation = GeoSiteRecommendation.builder()
                .id(snowflakeIdGenerator.nextId())
                .queryId(query.getId())
                .probeId(probeId)
                .monitorRecordId(monitorRecordId)
                .entityType(scope.type())
                .entityId(scope.id())
                .entityName(scope.name())
                .recommendationType(type)
                .title(truncate(title, 255))
                .currentGap(truncate(gap, 1000))
                .recommendedChange(StringUtils.hasText(recommendedChange) ? recommendedChange : "建议医院官网负责人补充可核验的事实、FAQ 和结构化摘要。")
                .contentBrief(toJson(brief))
                .evidenceJson(toJson(evidence))
                .executionMode(RECOMMENDATION_ONLY)
                .externalOwner("hospital_website_owner")
                .priority(Math.max(1, Math.min(priority == null ? 50 : priority, 100)))
                .complianceStatus(audit.isPassed() ? 1 : 2)
                .complianceResult(toJson(audit))
                .status(0)
                .build();
        siteRecommendationMapper.insert(recommendation);
        return recommendation;
    }

    private Map<String, Object> contentBrief(GeoQuery query, String type, String faqJson, String structuredSummary) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("question", query.getQueryText());
        brief.put("region", query.getRegion());
        brief.put("keyword", query.getDisease());
        brief.put("scenario", query.getTreatmentScenario());
        brief.put("recommendedSections", List.of("直接回答", "适用就诊场景", "科室选择说明", "常见问题", "来源与审核信息"));
        brief.put("schemaTypes", schemaTypes(query));
        brief.put("faq", StringUtils.hasText(faqJson) ? faqJson : "[]");
        brief.put("structuredSummary", StringUtils.hasText(structuredSummary) ? structuredSummary : "{}");
        brief.put("recommendationType", type);
        brief.put("publicationBoundary", "仅形成修改建议，由医院官网负责人审核和执行");
        return brief;
    }

    private List<String> schemaTypes(GeoQuery query) {
        List<String> types = new ArrayList<>(List.of("MedicalWebPage", "FAQPage"));
        if (query.getDoctorId() != null) {
            types.add("Physician");
        } else if (query.getDepartmentId() != null) {
            types.add("MedicalClinic");
        } else {
            types.add("Hospital");
        }
        return types;
    }

    private String buildMonitorChange(GeoQuery query, GeoMonitorRecord record, GeoFeedbackSuggestion suggestion) {
        return "建议医院官网负责人围绕“" + query.getQueryText() + "”评估以下修改：\n"
                + "1. 补充可核验的直接回答和适用场景；\n"
                + "2. 增加 FAQ、作者/审核者、发布日期与更新时间；\n"
                + "3. 补充 MedicalWebPage、FAQPage 及对应医疗实体结构化数据；\n"
                + "4. 对照监测证据核验引用来源，不作疗效承诺或个体诊疗建议。\n"
                + "内容建议摘要：" + truncate(suggestion.getContent(), 800);
    }

    private String buildProbeChange(GeoQuery query, GeoGapAnalysis analysis) {
        return "建议医院官网负责人围绕“" + query.getQueryText() + "”评估以下修改，系统不自动执行：\n"
                + "1. 仅使用医院已核验的机构、科室、医生、服务范围和诊疗特色事实，不新增未经确认的称号、排名或疗效表述；\n"
                + "2. 增加对用户问题的客观直接回答，并说明信息边界、适用就诊场景和线下评估必要性；\n"
                + "3. 补充 FAQ、作者/审核者、发布日期、更新时间和可追溯来源；\n"
                + "4. 建议医院方评估 MedicalWebPage、FAQPage 及对应医疗实体结构化数据；\n"
                + "5. 修改后按 1、3、7、14 天复测提及、引用 URL 和来源排名。\n"
                + "证据归类：" + analysis.getGapSummary();
    }

    private String recommendationType(Integer mentioned, Integer cited, Integer officialSourceCount) {
        if (safeInt(officialSourceCount) == 0) {
            return "source_authority";
        }
        if (safeInt(cited) == 0) {
            return "faq_schema";
        }
        if (safeInt(mentioned) == 0) {
            return "local_content";
        }
        return "content_structure";
    }

    private String recommendationTitle(String type) {
        return switch (type) {
            case "source_authority" -> "权威来源建设建议";
            case "faq_schema" -> "FAQ 与结构化数据建议";
            case "local_content" -> "本地化科普内容建议";
            default -> "内容结构优化建议";
        };
    }

    private EntityScope entityScope(GeoQuery query) {
        if (query.getDoctorId() != null) {
            return new EntityScope("doctor", query.getDoctorId(), value(query.getDoctorName(), "医生页面"));
        }
        if (query.getDepartmentId() != null) {
            return new EntityScope("department", query.getDepartmentId(), value(query.getDepartmentName(), "科室页面"));
        }
        return new EntityScope("hospital", query.getHospitalId(), value(query.getHospitalName(), "医院页面"));
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private record EntityScope(String type, Long id, String name) {
    }
}
