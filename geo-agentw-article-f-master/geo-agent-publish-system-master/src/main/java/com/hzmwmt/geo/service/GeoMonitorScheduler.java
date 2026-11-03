package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoMonitorRunRequest;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeoMonitorScheduler {

    private static final int[] RETEST_DAYS = {1, 3, 7, 14};

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoQueryService geoQueryService;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoMonitorTaskMapper monitorTaskMapper;

    @Resource
    private GeoEngineCatalog geoEngineCatalog;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    public List<GeoMonitorTask> scheduleManual(GeoMonitorRunRequest request) {
        return schedule(request, "manual", LocalDateTime.now().minusSeconds(1), false);
    }

    public List<GeoMonitorTask> scheduleBatch() {
        GeoMonitorRunRequest request = new GeoMonitorRunRequest();
        request.setLimit(geoProperties.getMonitorBatchSize());
        return schedule(request, "scheduled", LocalDateTime.now().minusSeconds(1), true);
    }

    public List<GeoMonitorTask> scheduleRetests(GeoMonitorRecord record, GeoQuery query, Long suggestionId) {
        if (record == null || query == null || record.getQueryId() == null || record.getEngine() == null) {
            return List.of();
        }
        List<GeoMonitorTask> tasks = new ArrayList<>();
        for (int day : RETEST_DAYS) {
            GeoMonitorTask task = buildTask(query, record.getEngine(), "retest", LocalDateTime.now().plusDays(day));
            task.setFeedbackSuggestionId(suggestionId);
            task.setRetestBatchId(suggestionId);
            Long existing = monitorTaskMapper.selectCount(new LambdaQueryWrapper<GeoMonitorTask>()
                    .eq(GeoMonitorTask::getFeedbackSuggestionId, suggestionId)
                    .eq(GeoMonitorTask::getRetestBatchId, suggestionId)
                    .ge(GeoMonitorTask::getPlannedTime, task.getPlannedTime().toLocalDate().atStartOfDay())
                    .lt(GeoMonitorTask::getPlannedTime, task.getPlannedTime().toLocalDate().plusDays(1).atStartOfDay()));
            if (existing == null || existing == 0) {
                monitorTaskMapper.insert(task);
                tasks.add(task);
            }
        }
        return tasks;
    }

    private List<GeoMonitorTask> schedule(GeoMonitorRunRequest request, String triggerType, LocalDateTime plannedTime, boolean dedupe) {
        List<GeoQuery> queries = resolveQueries(request);
        List<GeoProperties.EngineProperties> engines = geoEngineCatalog.enabledEngines(request == null ? null : request.getEngines());
        if (queries.isEmpty() || engines.isEmpty()) {
            return List.of();
        }
        List<GeoMonitorTask> tasks = new ArrayList<>();
        for (GeoQuery query : queries) {
            for (GeoProperties.EngineProperties engine : engines) {
                GeoMonitorTask task = buildTask(query, engine.getName(), triggerType, plannedTime);
                if (dedupe && hasOpenTask(task)) {
                    continue;
                }
                monitorTaskMapper.insert(task);
                tasks.add(task);
            }
        }
        return tasks;
    }

    private GeoMonitorTask buildTask(GeoQuery query, String engine, String triggerType, LocalDateTime plannedTime) {
        return GeoMonitorTask.builder()
                .id(snowflakeIdGenerator.nextId())
                .queryId(query.getId())
                .engine(engine)
                .queryText(query.getQueryText())
                .priority(query.getPriority() == null ? 50 : query.getPriority())
                .status(0)
                .triggerType(triggerType)
                .attemptCount(0)
                .maxAttempts(Math.max(1, geoProperties.getTaskMaxAttempts()))
                .plannedTime(plannedTime)
                .build();
    }

    private boolean hasOpenTask(GeoMonitorTask task) {
        Long count = monitorTaskMapper.selectCount(new LambdaQueryWrapper<GeoMonitorTask>()
                .eq(GeoMonitorTask::getQueryId, task.getQueryId())
                .eq(GeoMonitorTask::getEngine, task.getEngine())
                .in(GeoMonitorTask::getStatus, 0, 1)
                .ge(GeoMonitorTask::getPlannedTime, LocalDateTime.now().minusHours(1)));
        return count != null && count > 0;
    }

    private List<GeoQuery> resolveQueries(GeoMonitorRunRequest request) {
        if (request != null && request.getQueryId() != null) {
            GeoQuery query = geoQueryMapper.selectById(request.getQueryId());
            return query == null ? List.of() : List.of(query);
        }
        if (request != null && !CollectionUtils.isEmpty(request.getQueryIds())) {
            return geoQueryMapper.selectBatchIds(request.getQueryIds());
        }
        int limit = request == null || request.getLimit() == null ? geoProperties.getMonitorBatchSize() : request.getLimit();
        return geoQueryService.nextMonitorBatch(limit, geoProperties.getMonitorStaleHours());
    }
}
