package com.hzmwmt.geo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.a_publish.mapper.ArticleManagementMapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoMonitorRecordMapper;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.platform.GeoPlatformAdapter;
import com.hzmwmt.geo.service.platform.GeoPlatformRegistry;
import com.hzmwmt.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeoMonitorWorkerWorkflowTest {
    private GeoMonitorWorker worker;
    private GeoOptimizationService optimization;
    private GeoFeedbackWorkflowService feedback;

    @BeforeEach void setup() throws Exception {
        worker = new GeoMonitorWorker();
        GeoProperties properties = new GeoProperties();
        properties.setAutoOptimizationEnabled(true);
        inject("geoProperties", properties);
        inject("monitorTaskMapper", mock(GeoMonitorTaskMapper.class));
        inject("monitorRecordMapper", mock(GeoMonitorRecordMapper.class));
        var queries = mock(GeoQueryMapper.class);
        when(queries.selectById(1L)).thenReturn(GeoQuery.builder().id(1L).queryText("医院信息").build());
        inject("geoQueryMapper", queries);
        inject("articleManagementMapper", mock(ArticleManagementMapper.class));
        var engines = mock(GeoEngineCatalog.class);
        var engine = new GeoProperties.EngineProperties();
        when(engines.enabledEngine("qwen")).thenReturn(engine);
        inject("geoEngineCatalog", engines);
        var registry = mock(GeoPlatformRegistry.class);
        var adapter = mock(GeoPlatformAdapter.class);
        when(registry.resolve(engine)).thenReturn(adapter);
        inject("geoPlatformRegistry", registry);
        var calls = mock(GeoPlatformCallService.class);
        when(calls.ask(eq(engine), eq(adapter), anyString())).thenReturn(new GeoEngineAnswer("qwen", "回答"));
        inject("geoPlatformCallService", calls);
        var parser = mock(GeoEvidenceParser.class);
        when(parser.parse(any(), any(), anyLong())).thenReturn(GeoEvidence.builder().answerText("回答").build());
        inject("geoEvidenceParser", parser);
        inject("geoVisibilityScorer", mock(GeoVisibilityScorer.class));
        inject("geoSourceAssetService", mock(GeoSourceAssetService.class));
        optimization = mock(GeoOptimizationService.class);
        inject("geoOptimizationService", optimization);
        feedback = mock(GeoFeedbackWorkflowService.class);
        inject("feedbackWorkflow", feedback);
        inject("snowflakeIdGenerator", new SnowflakeIdGenerator(1, 1));
        inject("objectMapper", new ObjectMapper());
    }

    @Test void retestCompletesAndRefreshesItsOwnerWithoutRecursivelyGeneratingPlans() {
        GeoMonitorRecord record = ReflectionTestUtils.invokeMethod(worker, "runClaimedTask", task("retest"));
        assertThat(record.getStatus()).isEqualTo(1);
        verify(feedback).refreshRetestState(99L);
        verifyNoInteractions(optimization);
    }

    @Test void normalMonitoringStillGeneratesAnOptimizationForMissingEvidence() {
        GeoMonitorRecord record = ReflectionTestUtils.invokeMethod(worker, "runClaimedTask", task("scheduled"));
        assertThat(record.getStatus()).isEqualTo(1);
        verify(optimization).generateForMonitorRecord(record);
    }

    private GeoMonitorTask task(String trigger) {
        return GeoMonitorTask.builder().id(10L).queryId(1L).engine("qwen").triggerType(trigger)
                .feedbackSuggestionId(99L).attemptCount(1).maxAttempts(3).build();
    }
    private void inject(String field, Object value) { ReflectionTestUtils.setField(worker, field, value); }
}
