package com.hzmwmt.geo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.entity.GeoQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GeoGapClassifier {

    @Resource
    private ObjectMapper objectMapper;

    public GeoGapAnalysis classify(GeoQuery query, GeoEvidence evidence, String diagnosticAnswer) {
        GeoGapAnalysis fallback = fallback(query, evidence);
        if (!StringUtils.hasText(diagnosticAnswer)) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(diagnosticAnswer));
            String proposedGapType = safeCode(root.path("gapType").asText(fallback.getGapType()));
            boolean compatible = isCompatible(proposedGapType, evidence);
            return GeoGapAnalysis.builder()
                    .gapType(compatible && proposedGapType.equals(fallback.getGapType()) ? proposedGapType : fallback.getGapType())
                    .gapSummary(fallback.getGapSummary())
                    .sourceRequirements(fallback.getSourceRequirements())
                    .recommendedActions(fallback.getRecommendedActions())
                    .build();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private GeoGapAnalysis fallback(GeoQuery query, GeoEvidence evidence) {
        boolean mentioned = evidence != null && evidence.getMatchedTerms() != null && !evidence.getMatchedTerms().isEmpty();
        boolean cited = evidence != null && evidence.getCitedUrls() != null && !evidence.getCitedUrls().isEmpty();
        boolean official = evidence != null && evidence.getOfficialSourceCount() > 0;
        boolean competitors = evidence != null && evidence.getCompetitorMentions() != null && !evidence.getCompetitorMentions().isEmpty();

        String gapType;
        String summary;
        List<String> actions = new ArrayList<>();
        if (!mentioned && !cited) {
            gapType = "authority_and_relevance_gap";
            summary = "目标机构未被提及且答案没有可核验引用，当前公开内容与真实搜索意图的相关性和来源权威性均不足。";
            actions.add("向医院官网负责人提交本地化疾病/疗法科普页和 FAQ 的修改建议");
            actions.add("在可管理的发布渠道补充事实一致、可追溯来源的科普内容");
        } else if (!official) {
            gapType = "official_source_gap";
            summary = "平台返回了来源，但没有引用已配置的医院官方来源，缺少稳定、可索引的权威事实页。";
            actions.add("向医院官网负责人建议建设稳定的医院、科室、医生和诊疗特色事实页");
            actions.add("建议医院方为页面补充 FAQ 与医疗实体结构化数据并保持更新时间");
        } else if (competitors && !mentioned) {
            gapType = "competitive_authority_gap";
            summary = "同类机构获得了更高的来源权重，目标机构的公开证据覆盖度不足。";
            actions.add("整理差异化专科事实、专家资质和公开学术证据，形成官网修改建议稿");
            actions.add("通过政府、学术和权威媒体等第三方渠道补充可验证引用");
        } else {
            gapType = "content_structure_gap";
            summary = "已有一定公开证据，但页面对用户问题的直接回答、摘要和机器可读结构仍不完整。";
            actions.add("向医院官网负责人建议增加直接回答、FAQ、更新时间和结构化摘要");
            actions.add("发布后按 1、3、7、14 天复测并对比引用 URL 变化");
        }

        List<String> requirements = new ArrayList<>();
        requirements.add("医院官网可核验的机构、科室或医生事实页");
        requirements.add("政府、学术机构或权威媒体的独立公开资料");
        requirements.add("包含作者、审核者、发布日期和更新时间的医疗科普页");
        if (query != null && StringUtils.hasText(query.getRegion())) {
            requirements.add(query.getRegion() + "本地就诊场景和服务范围的客观说明");
        }
        return GeoGapAnalysis.builder()
                .gapType(gapType)
                .gapSummary(summary)
                .sourceRequirements(requirements)
                .recommendedActions(actions)
                .build();
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = truncate(item.asText(""), 500);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        });
        return values.stream().distinct().limit(10).toList();
    }

    private List<String> safeActions(List<String> actions) {
        List<String> safe = new ArrayList<>();
        for (String action : actions) {
            String normalized = action.toLowerCase(Locale.ROOT);
            if (normalized.contains("刷量") || normalized.contains("虚假") || normalized.contains("操纵")
                    || normalized.contains("训练公共模型") || normalized.contains("伪装用户")) {
                continue;
            }
            if (action.contains("修改官网") || action.contains("官网发布") || action.contains("提交收录")) {
                safe.add("向医院官网负责人建议：" + action);
            } else {
                safe.add(action);
            }
        }
        return safe.stream().distinct().limit(10).toList();
    }

    private boolean isCompatible(String gapType, GeoEvidence evidence) {
        if (!StringUtils.hasText(gapType) || evidence == null) {
            return false;
        }
        boolean mentioned = evidence.getMatchedTerms() != null && !evidence.getMatchedTerms().isEmpty();
        boolean cited = evidence.getCitedUrls() != null && !evidence.getCitedUrls().isEmpty();
        boolean official = evidence.getOfficialSourceCount() > 0;
        return switch (gapType) {
            case "authority_and_relevance_gap" -> !mentioned && !cited;
            case "official_source_gap" -> !official;
            case "competitive_authority_gap" -> !mentioned && evidence.getCompetitorMentions() != null
                    && !evidence.getCompetitorMentions().isEmpty();
            case "content_structure_gap" -> true;
            default -> false;
        };
    }

    private String safeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private String extractJson(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        return start >= 0 && end > start ? normalized.substring(start, end + 1) : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
