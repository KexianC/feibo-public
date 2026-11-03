package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.a_publish.mapper.ArticleManagementMapper;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class GeoMonitorWorker {

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoMonitorTaskMapper monitorTaskMapper;

    @Resource
    private GeoMonitorRecordMapper monitorRecordMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private ArticleManagementMapper articleManagementMapper;

    @Resource
    private GeoEngineCatalog geoEngineCatalog;

    @Resource
    private GeoPlatformRegistry geoPlatformRegistry;

    @Resource
    private GeoEvidenceParser geoEvidenceParser;

    @Resource
    private GeoVisibilityScorer geoVisibilityScorer;

    @Resource
    private GeoOptimizationService geoOptimizationService;

    @Resource
    private GeoPlatformCallService geoPlatformCallService;

    @Resource
    private GeoSourceAssetService geoSourceAssetService;

    @Resource
    private GeoFeedbackWorkflowService feedbackWorkflow;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(Math.max(1, geoProperties.getWorkerConcurrency()));
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${app.geo.worker-poll-ms:15000}")
    public void pollPendingTasks() {
        if (!geoProperties.isMonitorEnabled()) {
            return;
        }
        try {
            int count = processPending(false).size();
            if (count > 0) {
                log.info("GEO monitor worker submitted pending tasks: count={}", count);
            }
        } catch (Exception e) {
            log.warn("GEO monitor worker polling failed: exceptionType={}", e.getClass().getSimpleName());
        }
    }

    public List<GeoMonitorRecord> processPending(boolean wait) {
        List<GeoMonitorTask> tasks = monitorTaskMapper.selectList(new LambdaQueryWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getStatus, 0)
                .le(GeoMonitorTask::getPlannedTime, LocalDateTime.now())
                .orderByDesc(GeoMonitorTask::getPriority)
                .orderByAsc(GeoMonitorTask::getPlannedTime)
                .last("LIMIT " + Math.max(1, geoProperties.getWorkerBatchSize())));
        return processTasks(tasks, wait);
    }

    public List<GeoMonitorRecord> processTasks(List<GeoMonitorTask> tasks, boolean wait) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<GeoMonitorRecord>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> claimAndRun(task), executorService))
                .toList();
        if (!wait) {
            return List.of();
        }
        List<GeoMonitorRecord> records = new ArrayList<>();
        for (CompletableFuture<GeoMonitorRecord> future : futures) {
            try {
                GeoMonitorRecord record = future.join();
                if (record != null) {
                    records.add(record);
                }
            } catch (Exception e) {
                log.warn("GEO monitor worker task future failed: exceptionType={}",
                        e.getClass().getSimpleName());
            }
        }
        return records;
    }

    private GeoMonitorRecord claimAndRun(GeoMonitorTask task) {
        if (task == null || task.getId() == null) {
            return null;
        }
        int nextAttempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
        GeoMonitorTask claim = new GeoMonitorTask();
        claim.setId(task.getId());
        claim.setStatus(1);
        claim.setStartedTime(LocalDateTime.now());
        claim.setAttemptCount(nextAttempt);
        int updated = monitorTaskMapper.update(claim, new LambdaQueryWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getId, task.getId())
                .eq(GeoMonitorTask::getStatus, 0)
                .le(GeoMonitorTask::getPlannedTime, LocalDateTime.now()));
        if (updated < 1) {
            return null;
        }
        GeoMonitorTask claimed = monitorTaskMapper.selectById(task.getId());
        refreshFeedback(task);
        return runClaimedTask(claimed == null ? task : claimed);
    }

    private GeoMonitorRecord runClaimedTask(GeoMonitorTask task) {
        GeoQuery query = geoQueryMapper.selectById(task.getQueryId());
        GeoMonitorRecord record = baseRecord(task, query);
        try {
            if (query == null) {
                throw new IllegalStateException("GEO query not found: " + task.getQueryId());
            }
            GeoProperties.EngineProperties engine = geoEngineCatalog.enabledEngine(task.getEngine());
            GeoPlatformAdapter adapter = geoPlatformRegistry.resolve(engine);
            long startedAt = System.currentTimeMillis();
            GeoEngineAnswer answer = geoPlatformCallService.ask(engine, adapter, query.getQueryText());
            long measuredLatencyMs = System.currentTimeMillis() - startedAt;
            GeoEvidence evidence = geoEvidenceParser.parse(query, answer, measuredLatencyMs);
            fillSuccess(record, evidence);
            monitorRecordMapper.insert(record);
            geoSourceAssetService.capture(query, record, evidence);
            markTaskSuccess(task, record);
            updateQueryMonitorTime(query);
            // Retests evaluate the existing plan; they must not recursively spawn four more retests.
            if (!"retest".equals(task.getTriggerType())) triggerOptimizationIfNeeded(record);
            return record;
        } catch (Exception e) {
            log.warn("GEO monitor task failed: taskId={}, queryId={}, exceptionType={}",
                    task.getId(), task.getQueryId(), e.getClass().getSimpleName());
            fillFailure(record, e);
            monitorRecordMapper.insert(record);
            markTaskFailure(task, record, e);
            return record;
        }
    }

    private GeoMonitorRecord baseRecord(GeoMonitorTask task, GeoQuery query) {
        GeoMonitorRecord record = new GeoMonitorRecord();
        record.setId(snowflakeIdGenerator.nextId());
        record.setQueryId(task.getQueryId());
        record.setEngine(task.getEngine());
        record.setQueryText(StringUtils.hasText(task.getQueryText()) ? task.getQueryText() : query == null ? "" : query.getQueryText());
        record.setMonitoredTime(LocalDateTime.now());
        record.setArticleId(query == null ? null : findRelatedArticleId(query));
        return record;
    }

    private void fillSuccess(GeoMonitorRecord record, GeoEvidence evidence) {
        record.setAnswerText(evidence.getAnswerText());
        record.setCited(evidence.getCitedUrls().isEmpty() ? 0 : 1);
        record.setMentioned(evidence.getMatchedTerms().isEmpty() ? 0 : 1);
        record.setCitedUrls(toJson(evidence.getCitedUrls()));
        record.setMatchedTerms(toJson(evidence.getMatchedTerms()));
        record.setRankPosition(evidence.getRankPosition());
        record.setSourceCount(evidence.getSourceCount());
        record.setOfficialSourceCount(evidence.getOfficialSourceCount());
        record.setCompetitorMentions(toJson(evidence.getCompetitorMentions()));
        record.setRawPayload(evidence.getRawPayload());
        record.setLatencyMs(evidence.getLatencyMs());
        record.setVisibilityScore(geoVisibilityScorer.score(evidence));
        record.setStatus(1);
    }

    private void fillFailure(GeoMonitorRecord record, Exception e) {
        record.setStatus(2);
        record.setMentioned(0);
        record.setCited(0);
        record.setVisibilityScore(0);
        record.setErrorMessage(e.getMessage() == null ? "monitor failed" : truncate(e.getMessage(), 1000));
    }

    private void markTaskSuccess(GeoMonitorTask task, GeoMonitorRecord record) {
        GeoMonitorTask update = new GeoMonitorTask();
        update.setId(task.getId());
        update.setStatus(2);
        update.setFinishedTime(LocalDateTime.now());
        update.setMonitorRecordId(record.getId());
        monitorTaskMapper.updateById(update);
        refreshFeedback(task);
    }

    private void markTaskFailure(GeoMonitorTask task, GeoMonitorRecord record, Exception e) {
        int attempts = task.getAttemptCount() == null ? 1 : task.getAttemptCount();
        int maxAttempts = task.getMaxAttempts() == null ? 1 : task.getMaxAttempts();
        GeoMonitorTask update = new GeoMonitorTask();
        update.setId(task.getId());
        update.setMonitorRecordId(record.getId());
        update.setErrorMessage(e.getMessage() == null ? "monitor failed" : truncate(e.getMessage(), 1000));
        if (attempts < maxAttempts) {
            LocalDateTime retryAt = LocalDateTime.now().plusMinutes(Math.max(1, geoProperties.getTaskRetryDelayMinutes()));
            update.setStatus(0);
            update.setNextRetryTime(retryAt);
            update.setPlannedTime(retryAt);
        } else {
            update.setStatus(3);
            update.setFinishedTime(LocalDateTime.now());
        }
        monitorTaskMapper.updateById(update);
        refreshFeedback(task);
    }

    private void refreshFeedback(GeoMonitorTask task) {
        if (task.getFeedbackSuggestionId() == null) return;
        try {
            feedbackWorkflow.refreshRetestState(task.getFeedbackSuggestionId());
        } catch (Exception error) {
            // A presentation-state failure must not turn a completed model call into a failed task.
            log.warn("GEO feedback retest state refresh failed: taskId={}, exceptionType={}", task.getId(), error.getClass().getSimpleName());
        }
    }

    private void updateQueryMonitorTime(GeoQuery query) {
        GeoQuery update = new GeoQuery();
        update.setId(query.getId());
        update.setLastMonitorTime(LocalDateTime.now());
        geoQueryMapper.updateById(update);
    }

    private void triggerOptimizationIfNeeded(GeoMonitorRecord record) {
        if (!geoProperties.isAutoOptimizationEnabled() || record.getStatus() == null || record.getStatus() != 1) {
            return;
        }
        boolean missed = Objects.equals(record.getMentioned(), 0)
                || Objects.equals(record.getCited(), 0)
                || record.getOfficialSourceCount() == null
                || record.getOfficialSourceCount() == 0;
        if (!missed) {
            return;
        }
        try {
            geoOptimizationService.generateForMonitorRecord(record);
        } catch (Exception e) {
            log.warn("GEO auto optimization failed: recordId={}, exceptionType={}",
                    record.getId(), e.getClass().getSimpleName());
        }
    }

    private Long findRelatedArticleId(GeoQuery query) {
        ArticleInfo article = articleManagementMapper.selectOne(new LambdaQueryWrapper<ArticleInfo>()
                .eq(query.getDoctorId() != null, ArticleInfo::getDoctorId, query.getDoctorId())
                .like(StringUtils.hasText(query.getDisease()), ArticleInfo::getContent, query.getDisease())
                .orderByDesc(ArticleInfo::getUpdatedTime)
                .last("LIMIT 1"));
        return article == null ? null : article.getId();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
