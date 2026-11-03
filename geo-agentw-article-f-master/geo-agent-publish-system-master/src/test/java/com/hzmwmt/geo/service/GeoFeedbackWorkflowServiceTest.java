package com.hzmwmt.geo.service;

import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoFeedbackSuggestionMapper;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.dto.GeoFeedbackHandlingRequest;
import com.hzmwmt.geo.model.dto.GeoFeedbackPublicationRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeoFeedbackWorkflowServiceTest {
    private GeoFeedbackWorkflowService service;
    private GeoFeedbackSuggestionMapper suggestions;
    private GeoMonitorTaskMapper tasks;
    private MedicalComplianceService compliance;
    private GeoFeedbackSuggestion suggestion;

    @BeforeEach void setup() {
        service = new GeoFeedbackWorkflowService();
        suggestions = mock(GeoFeedbackSuggestionMapper.class);
        tasks = mock(GeoMonitorTaskMapper.class);
        compliance = mock(MedicalComplianceService.class);
        ReflectionTestUtils.setField(service, "suggestions", suggestions);
        ReflectionTestUtils.setField(service, "tasks", tasks);
        ReflectionTestUtils.setField(service, "compliance", compliance);
        GeoProperties properties = new GeoProperties();
        properties.setMonitorEnabled(true);
        ReflectionTestUtils.setField(service, "properties", properties);
        suggestion = GeoFeedbackSuggestion.builder().id(11L).title("已核验事实").content("健康科普")
                .activeRetestBatchId(33L).status(0).build();
        when(suggestions.selectOne(any())).thenReturn(suggestion);
        when(suggestions.selectById(11L)).thenReturn(suggestion);
        LoginContextHolder.setUser(7L, "operator", "运营", "ADMIN", true);
    }

    @AfterEach void clear() { LoginContextHolder.clear(); }

    @Test void anonymousCannotRecordHandling() {
        LoginContextHolder.clear();
        assertThatThrownBy(() -> service.updateHandling(11L, new GeoFeedbackHandlingRequest())).isInstanceOf(AppException.class);
        verify(suggestions, never()).updateById(any(GeoFeedbackSuggestion.class));
    }

    @Test void processedRequiresNoteAndPersistsAuthenticatedActor() {
        GeoFeedbackHandlingRequest request = new GeoFeedbackHandlingRequest();
        request.setStatus(1);
        assertThatThrownBy(() -> service.updateHandling(11L, request)).isInstanceOf(AppException.class);
        request.setNote("已交医院官网负责人核验");
        service.updateHandling(11L, request);
        ArgumentCaptor<GeoFeedbackSuggestion> update = ArgumentCaptor.forClass(GeoFeedbackSuggestion.class);
        verify(suggestions).updateById(update.capture());
        assertThat(update.getValue().getHandledBy()).isEqualTo(7L);
        assertThat(update.getValue().getStatus()).isEqualTo(1);
        assertThat(update.getValue().getProcessingNote()).contains("官网负责人");
        assertThat(update.getValue().getPublishStatus()).isNull();
    }

    @Test void manualPublicationRequiresComplianceAndRecordsEvidenceWithoutSchedulingOrPublishing() {
        GeoFeedbackPublicationRequest request = new GeoFeedbackPublicationRequest();
        request.setPublishedUrl("https://hospital.example/education/1");
        when(compliance.audit(anyString(), anyString())).thenReturn(ComplianceAuditResult.builder().passed(false).build());
        assertThatThrownBy(() -> service.confirmExternalPublication(11L, request)).isInstanceOf(AppException.class);
        verify(suggestions, never()).updateById(any(GeoFeedbackSuggestion.class));
        when(compliance.audit(anyString(), anyString())).thenReturn(ComplianceAuditResult.builder().passed(true).build());
        service.confirmExternalPublication(11L, request);
        ArgumentCaptor<GeoFeedbackSuggestion> update = ArgumentCaptor.forClass(GeoFeedbackSuggestion.class);
        verify(suggestions).updateById(update.capture());
        assertThat(update.getValue().getManualPublishedUrl()).isEqualTo(request.getPublishedUrl());
        assertThat(update.getValue().getPublicationConfirmedBy()).isEqualTo(7L);
        assertThat(update.getValue().getPublicationConfirmedTime()).isNotNull();
        assertThat(update.getValue().getPublishStatus()).isEqualTo(2);
        verifyNoInteractions(tasks);
    }

    @Test void publicationRejectsExecutableOrCredentialUrls() {
        for (String url : List.of("javascript:alert(1)", "data:text/html,x", "https://user:pass@example.org/a", "https://"))
            assertThatThrownBy(() -> GeoFeedbackWorkflowService.validatePublicationUrl(url)).isInstanceOf(AppException.class);
    }

    @Test void immediateRetestAcceleratesOneScheduledTaskWithoutDuplicatingThePlan() {
        GeoMonitorTask queued = GeoMonitorTask.builder().id(21L).status(0).plannedTime(LocalDateTime.now().plusDays(3)).build();
        when(tasks.selectList(any())).thenReturn(List.of(queued));
        service.requestRetest(11L);
        ArgumentCaptor<GeoMonitorTask> task = ArgumentCaptor.forClass(GeoMonitorTask.class);
        verify(tasks).updateById(task.capture());
        assertThat(task.getValue().getId()).isEqualTo(21L);
        assertThat(task.getValue().getPlannedTime()).isBefore(LocalDateTime.now());
        verify(tasks, never()).insert(any(GeoMonitorTask.class));
    }

    @Test void failedTerminalTasksAreNotReportedAsCompletedAndPendingRetriesRemainScheduled() {
        assertThat(status(2, 0)).isEqualTo(1);
        assertThat(status(2, 1)).isEqualTo(2);
        assertThat(status(2, 3)).isEqualTo(4);
        assertThat(status(2, 2)).isEqualTo(3);
    }

    @Test void taskCompletionPersistsFinalTimeAndState() {
        LocalDateTime finish = LocalDateTime.of(2026, 9, 9, 10, 0);
        when(tasks.selectList(any())).thenReturn(List.of(GeoMonitorTask.builder().status(2).finishedTime(finish).build()));
        service.refreshRetestState(11L);
        ArgumentCaptor<GeoFeedbackSuggestion> update = ArgumentCaptor.forClass(GeoFeedbackSuggestion.class);
        verify(suggestions).updateById(update.capture());
        assertThat(update.getValue().getRetestStatus()).isEqualTo(3);
        assertThat(update.getValue().getLastRetestTime()).isEqualTo(finish);
    }

    private int status(int first, int second) {
        return GeoFeedbackWorkflowService.retestStatus(List.of(GeoMonitorTask.builder().status(first).build(),
                GeoMonitorTask.builder().status(second).build()));
    }
}
