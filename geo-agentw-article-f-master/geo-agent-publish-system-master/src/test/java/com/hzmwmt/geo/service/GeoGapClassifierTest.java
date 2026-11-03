package com.hzmwmt.geo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.entity.GeoQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoGapClassifierTest {

    private GeoGapClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new GeoGapClassifier();
        ReflectionTestUtils.setField(classifier, "objectMapper", new ObjectMapper());
    }

    @Test
    void evidenceOverridesContradictoryModelDiagnosis() {
        GeoQuery query = GeoQuery.builder()
                .region("杭州")
                .hospitalName("杭州市中医院")
                .departmentName("皮肤科")
                .build();
        GeoEvidence evidence = GeoEvidence.builder()
                .matchedTerms(List.of("杭州市中医院"))
                .citedUrls(List.of("https://hztcm.cn/example"))
                .officialSourceCount(2)
                .build();
        String modelDiagnosis = """
                {"gapType":"official_source_gap","gapSummary":"没有官方来源",
                 "sourceRequirements":["虚构来源"],
                 "recommendedActions":["自动修改官网并堆砌SEO关键词"]}
                """;

        GeoGapAnalysis result = classifier.classify(query, evidence, modelDiagnosis);

        assertThat(result.getGapType()).isEqualTo("content_structure_gap");
        assertThat(result.getGapSummary()).contains("已有一定公开证据");
        assertThat(result.getRecommendedActions()).noneMatch(value -> value.contains("自动修改官网") || value.contains("SEO"));
    }

    @Test
    void missingMentionAndCitationRemainAuthorityAndRelevanceGap() {
        GeoEvidence evidence = GeoEvidence.builder().build();

        GeoGapAnalysis result = classifier.classify(
                GeoQuery.builder().region("杭州").build(),
                evidence,
                "{\"gapType\":\"content_structure_gap\",\"gapSummary\":\"模型自述\"}"
        );

        assertThat(result.getGapType()).isEqualTo("authority_and_relevance_gap");
        assertThat(result.getGapSummary()).contains("未被提及");
    }
}
