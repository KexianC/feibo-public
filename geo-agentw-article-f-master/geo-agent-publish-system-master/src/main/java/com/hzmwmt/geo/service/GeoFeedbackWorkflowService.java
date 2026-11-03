package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoFeedbackSuggestionMapper;
import com.hzmwmt.geo.mapper.GeoMonitorRecordMapper;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoFeedbackHandlingRequest;
import com.hzmwmt.geo.model.dto.GeoFeedbackPublicationRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Records operator actions; it never publishes content or writes to a hospital website. */
@Service
public class GeoFeedbackWorkflowService {
    @Resource private GeoFeedbackSuggestionMapper suggestions;
    @Resource private GeoMonitorTaskMapper tasks;
    @Resource private GeoMonitorRecordMapper records;
    @Resource private GeoQueryMapper queries;
    @Resource private GeoProperties properties;
    @Resource private GeoEngineCatalog engines;
    @Resource private MedicalComplianceService compliance;
    @Resource private SnowflakeIdGenerator ids;

    public GeoFeedbackSuggestion detail(Long id) {
        requireActor();
        GeoFeedbackSuggestion result = suggestions.selectById(id);
        if (result == null) throw new AppException("GEO_FEEDBACK_NOT_FOUND", "优化建议不存在", HttpStatus.NOT_FOUND);
        return result;
    }

    @Transactional
    public GeoFeedbackSuggestion updateHandling(Long id, GeoFeedbackHandlingRequest request) {
        Long actor = requireActor();
        GeoFeedbackSuggestion existing = lockSuggestion(id);
        if (request == null || request.getStatus() == null || request.getStatus() < 0 || request.getStatus() > 1)
            throw new AppException("PARAM_INVALID", "处理状态不合法");
        String note = request.getNote() == null ? "" : request.getNote().trim();
        if (note.length() > 2000 || (request.getStatus() == 1 && note.isEmpty()))
            throw new AppException("PARAM_INVALID", "标记已处理时请填写处理说明，最多2000字");
        GeoFeedbackSuggestion update = new GeoFeedbackSuggestion();
        update.setId(existing.getId());
        update.setStatus(request.getStatus());
        update.setProcessingNote(note);
        update.setHandledBy(actor);
        suggestions.updateById(update);
        return suggestions.selectById(id);
    }

    @Transactional
    public GeoFeedbackSuggestion confirmExternalPublication(Long id, GeoFeedbackPublicationRequest request) {
        Long actor = requireActor();
        GeoFeedbackSuggestion existing = lockSuggestion(id);
        String url = validatePublicationUrl(request == null ? null : request.getPublishedUrl());
        if (!compliance.audit(existing.getTitle(), existing.getContent() + "\n"
                + existing.getFaqJson() + "\n" + existing.getStructuredSummary()).isPassed())
            throw new AppException("MEDICAL_COMPLIANCE_BLOCKED", "建议未通过合规审核，不能登记已发布");
        GeoFeedbackSuggestion update = new GeoFeedbackSuggestion();
        update.setId(id);
        update.setPublishStatus(2);
        update.setManualPublishedUrl(url);
        update.setPublicationConfirmedBy(actor);
        update.setPublicationConfirmedTime(LocalDateTime.now());
        suggestions.updateById(update);
        return suggestions.selectById(id);
    }

    @Transactional
    public GeoFeedbackSuggestion requestRetest(Long id) {
        requireActor();
        GeoFeedbackSuggestion suggestion = lockSuggestion(id);
        if (!properties.isMonitorEnabled()) throw new AppException("GEO_MONITOR_DISABLED", "监测服务尚未启用，无法执行复测");
        List<GeoMonitorTask> linked = linkedTasks(suggestion);
        if (linked.stream().anyMatch(t -> Objects.equals(t.getStatus(), 1))) return suggestion;
        GeoMonitorTask pending = linked.stream().filter(t -> Objects.equals(t.getStatus(), 0))
                .min(Comparator.comparing(GeoMonitorTask::getPlannedTime)).orElse(null);
        if (pending != null) {
            GeoMonitorTask update = new GeoMonitorTask();
            update.setId(pending.getId());
            update.setPlannedTime(LocalDateTime.now().minusSeconds(1));
            tasks.updateById(update);
        } else {
            var original = records.selectById(suggestion.getMonitorRecordId());
            var query = queries.selectById(suggestion.getQueryId());
            if (original == null || query == null) throw new AppException("GEO_QUERY_NOT_FOUND", "原问题或监测记录不存在");
            engines.enabledEngine(original.getEngine());
            Long batchId = ids.nextId();
            GeoMonitorTask task = GeoMonitorTask.builder().id(ids.nextId()).feedbackSuggestionId(id)
                    .retestBatchId(batchId)
                    .queryId(query.getId()).queryText(query.getQueryText()).engine(original.getEngine())
                    .priority(query.getPriority() == null ? 50 : query.getPriority()).status(0).triggerType("retest")
                    .attemptCount(0).maxAttempts(Math.max(1, properties.getTaskMaxAttempts()))
                    .plannedTime(LocalDateTime.now().minusSeconds(1)).build();
            tasks.insert(task);
            GeoFeedbackSuggestion batchUpdate = new GeoFeedbackSuggestion();
            batchUpdate.setId(id);
            batchUpdate.setActiveRetestBatchId(batchId);
            suggestions.updateById(batchUpdate);
            suggestion.setActiveRetestBatchId(batchId);
        }
        refreshLocked(suggestion);
        return suggestions.selectById(id);
    }

    /** Invoked after a task is claimed or reaches pending/success/terminal failure. */
    @Transactional
    public void refreshRetestState(Long suggestionId) {
        if (suggestionId == null) return; // Do not guess the owner of legacy tasks.
        refreshLocked(lockSuggestion(suggestionId));
    }

    private void refreshLocked(GeoFeedbackSuggestion suggestion) {
        List<GeoMonitorTask> linked = linkedTasks(suggestion);
        if (linked.isEmpty()) return;
        GeoFeedbackSuggestion update = new GeoFeedbackSuggestion();
        update.setId(suggestion.getId());
        update.setRetestStatus(retestStatus(linked));
        linked.stream().map(GeoMonitorTask::getFinishedTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).ifPresent(update::setLastRetestTime);
        suggestions.updateById(update);
    }

    static int retestStatus(List<GeoMonitorTask> linked) {
        if (linked.stream().anyMatch(t -> Objects.equals(t.getStatus(), 1))) return 2;
        if (linked.stream().anyMatch(t -> Objects.equals(t.getStatus(), 0))) return 1;
        return linked.stream().anyMatch(t -> Objects.equals(t.getStatus(), 3)) ? 4 : 3;
    }

    private List<GeoMonitorTask> linkedTasks(GeoFeedbackSuggestion suggestion) {
        if (suggestion.getActiveRetestBatchId() == null) return List.of();
        return tasks.selectList(new LambdaQueryWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getFeedbackSuggestionId, suggestion.getId())
                .eq(GeoMonitorTask::getRetestBatchId, suggestion.getActiveRetestBatchId()));
    }

    private GeoFeedbackSuggestion lockSuggestion(Long id) {
        GeoFeedbackSuggestion result = suggestions.selectOne(new LambdaQueryWrapper<GeoFeedbackSuggestion>()
                .eq(GeoFeedbackSuggestion::getId, id).last("FOR UPDATE"));
        if (result == null) throw new AppException("GEO_FEEDBACK_NOT_FOUND", "优化建议不存在", HttpStatus.NOT_FOUND);
        return result;
    }

    private Long requireActor() {
        var actor = LoginContextHolder.currentUser();
        if (actor == null || !actor.loggedIn() || actor.userId() == null)
            throw new AppException("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED);
        return actor.userId();
    }

    static String validatePublicationUrl(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() > 2000) throw new AppException("PARAM_INVALID", "请填写已发布内容的HTTP或HTTPS链接");
        try {
            URI uri = URI.create(raw.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) throw new IllegalArgumentException();
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new AppException("PARAM_INVALID", "发布链接必须是有效的HTTP或HTTPS地址");
        }
    }
}
