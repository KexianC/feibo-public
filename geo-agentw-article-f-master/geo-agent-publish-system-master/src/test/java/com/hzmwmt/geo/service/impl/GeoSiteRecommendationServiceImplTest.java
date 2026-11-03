package com.hzmwmt.geo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.mapper.GeoSiteRecommendationMapper;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSiteRecommendation;
import com.hzmwmt.geo.service.MedicalComplianceService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoSiteRecommendationServiceImplTest {

    private GeoSiteRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        GeoSiteRecommendationMapper mapper = mock(GeoSiteRecommendationMapper.class);
        MedicalComplianceService complianceService = mock(MedicalComplianceService.class);
        when(complianceService.audit(anyString(), anyString()))
                .thenReturn(ComplianceAuditResult.builder().passed(true).level("low").build());

        service = new GeoSiteRecommendationServiceImpl();
        ReflectionTestUtils.setField(service, "siteRecommendationMapper", mapper);
        ReflectionTestUtils.setField(service, "medicalComplianceService", complianceService);
        ReflectionTestUtils.setField(service, "snowflakeIdGenerator", new SnowflakeIdGenerator(1, 1));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void officialSiteRecommendationIsManualOnlyAndDoesNotReuseModelActions() {
        GeoQuery query = GeoQuery.builder()
                .id(1L)
                .departmentId(2L)
                .departmentName("皮肤科")
                .queryText("杭州哪里看痘痘比较正规？")
                .priority(80)
                .build();
        GeoDialogueProbe probe = GeoDialogueProbe.builder()
                .id(3L)
                .engine("qwen")
                .probeMode("platform_self_review")
                .mentioned(0)
                .cited(0)
                .officialSourceCount(0)
                .build();
        GeoGapAnalysis analysis = GeoGapAnalysis.builder()
                .gapType("authority_and_relevance_gap")
                .gapSummary("公开证据不足")
                .sourceRequirements(List.of("医院官网事实页"))
                .recommendedActions(List.of("自动修改官网并新增未经核验的专病中心称号"))
                .build();

        GeoSiteRecommendation result = service.createFromProbe(query, probe, analysis);

        assertThat(result.getExecutionMode()).isEqualTo("recommendation_only");
        assertThat(result.getExternalOwner()).isEqualTo("hospital_website_owner");
        assertThat(result.getStatus()).isZero();
        assertThat(result.getRecommendedChange()).contains("系统不自动执行");
        assertThat(result.getRecommendedChange()).contains("不新增未经确认的称号");
        assertThat(result.getRecommendedChange()).doesNotContain("专病中心称号");
    }
}
