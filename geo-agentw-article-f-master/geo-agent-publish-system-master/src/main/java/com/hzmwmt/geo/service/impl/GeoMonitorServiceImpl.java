package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoMonitorRecordMapper;
import com.hzmwmt.geo.mapper.GeoMonitorTaskMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoMonitorRunRequest;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.GeoEngineCatalog;
import com.hzmwmt.geo.service.GeoOfficialDomainRegistry;
import com.hzmwmt.geo.service.GeoMonitorScheduler;
import com.hzmwmt.geo.service.GeoMonitorService;
import com.hzmwmt.geo.service.GeoMonitorWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GeoMonitorServiceImpl implements GeoMonitorService {

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoMonitorScheduler geoMonitorScheduler;

    @Resource
    private GeoMonitorWorker geoMonitorWorker;

    @Resource
    private GeoMonitorRecordMapper monitorRecordMapper;

    @Resource
    private GeoMonitorTaskMapper monitorTaskMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoEngineCatalog geoEngineCatalog;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private GeoOfficialDomainRegistry officialDomainRegistry;

    @Override
    public List<GeoMonitorRecord> runMonitor(GeoMonitorRunRequest request) {
        List<GeoMonitorTask> tasks = geoMonitorScheduler.scheduleManual(request);
        return geoMonitorWorker.processTasks(tasks, true);
    }

    @Override
    public List<GeoMonitorRecord> runScheduledBatch() {
        if (!geoProperties.isMonitorEnabled()) {
            log.info("GEO monitor skipped because app.geo.monitor-enabled=false");
            return List.of();
        }
        List<GeoMonitorTask> tasks = geoMonitorScheduler.scheduleBatch();
        return geoMonitorWorker.processTasks(tasks, true);
    }

    @Override
    public IPage<GeoMonitorRecord> page(Page<GeoMonitorRecord> page, Long queryId, String engine, Integer mentioned, Integer cited) {
        LambdaQueryWrapper<GeoMonitorRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryId != null, GeoMonitorRecord::getQueryId, queryId);
        wrapper.eq(StringUtils.hasText(engine), GeoMonitorRecord::getEngine, engine);
        wrapper.eq(mentioned != null, GeoMonitorRecord::getMentioned, mentioned);
        wrapper.eq(cited != null, GeoMonitorRecord::getCited, cited);
        wrapper.orderByDesc(GeoMonitorRecord::getMonitoredTime);
        return monitorRecordMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<GeoMonitorTask> pageTasks(Page<GeoMonitorTask> page, Integer status, String engine, String triggerType) {
        LambdaQueryWrapper<GeoMonitorTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, GeoMonitorTask::getStatus, status);
        wrapper.eq(StringUtils.hasText(engine), GeoMonitorTask::getEngine, engine);
        wrapper.eq(StringUtils.hasText(triggerType), GeoMonitorTask::getTriggerType, triggerType);
        wrapper.orderByDesc(GeoMonitorTask::getCreatedTime);
        return monitorTaskMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> platformMatrix(int limit) {
        List<GeoMonitorRecord> records = latestRecords(Math.max(50, limit));
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (GeoMonitorRecord record : records) {
            String rowKey = String.valueOf(record.getQueryId());
            Map<String, Object> row = rows.computeIfAbsent(rowKey, key -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("queryId", record.getQueryId());
                value.put("queryText", record.getQueryText());
                value.put("platforms", new LinkedHashMap<String, Object>());
                return value;
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> platforms = (Map<String, Object>) row.get("platforms");
            if (!platforms.containsKey(record.getEngine())) {
                platforms.put(record.getEngine(), Map.of(
                        "mentioned", safeInt(record.getMentioned()),
                        "cited", safeInt(record.getCited()),
                        "rankPosition", record.getRankPosition() == null ? "" : record.getRankPosition(),
                        "visibilityScore", safeInt(record.getVisibilityScore()),
                        "officialSourceCount", safeInt(record.getOfficialSourceCount()),
                        "competitorMentions", record.getCompetitorMentions() == null ? "[]" : record.getCompetitorMentions(),
                        "monitoredTime", record.getMonitoredTime()
                ));
            }
        }
        return Map.of(
                "engines", geoEngineCatalog.enabledEngines(null).stream().map(GeoProperties.EngineProperties::getName).toList(),
                "rows", rows.values().stream().limit(Math.max(1, limit)).toList()
        );
    }

    @Override
    public Map<String, Object> trend(String dimension, int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDateTime from = LocalDateTime.now().minusDays(safeDays);
        List<GeoMonitorRecord> records = monitorRecordMapper.selectList(new LambdaQueryWrapper<GeoMonitorRecord>()
                .eq(GeoMonitorRecord::getStatus, 1)
                .ge(GeoMonitorRecord::getMonitoredTime, from)
                .orderByAsc(GeoMonitorRecord::getMonitoredTime)
                .last("LIMIT 3000"));
        Map<Long, GeoQuery> queryCache = new ConcurrentHashMap<>();
        Map<String, ScoreAccumulator> buckets = new LinkedHashMap<>();
        for (GeoMonitorRecord record : records) {
            GeoQuery query = queryCache.computeIfAbsent(record.getQueryId(), geoQueryMapper::selectById);
            if (query == null) {
                continue;
            }
            String scopeName = scopeName(query, dimension);
            if (!StringUtils.hasText(scopeName)) {
                continue;
            }
            LocalDate date = record.getMonitoredTime() == null ? LocalDate.now() : record.getMonitoredTime().toLocalDate();
            String key = date + "|" + scopeName;
            buckets.computeIfAbsent(key, ignored -> new ScoreAccumulator(date.toString(), scopeName))
                    .add(safeInt(record.getVisibilityScore()));
        }
        List<Map<String, Object>> points = buckets.values().stream()
                .map(ScoreAccumulator::toMap)
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("date")) + String.valueOf(item.get("scopeName"))))
                .toList();
        return Map.of("dimension", StringUtils.hasText(dimension) ? dimension : "hospital", "days", safeDays, "points", points);
    }

    @Override
    public Map<String, Object> sourceAnalysis(int limit) {
        List<GeoMonitorRecord> records = latestRecords(Math.max(100, limit * 5));
        Map<String, SourceAccumulator> sources = new LinkedHashMap<>();
        for (GeoMonitorRecord record : records) {
            for (String url : parseUrls(record.getCitedUrls())) {
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                sources.computeIfAbsent(url, SourceAccumulator::new).add(record, officialDomainRegistry.isOfficialUrl(url));
            }
        }
        List<Map<String, Object>> rows = sources.values().stream()
                .sorted(Comparator.comparing(SourceAccumulator::getCount).reversed())
                .limit(Math.max(1, limit))
                .map(SourceAccumulator::toMap)
                .toList();
        return Map.of("rows", rows);
    }

    private List<GeoMonitorRecord> latestRecords(int limit) {
        return monitorRecordMapper.selectList(new LambdaQueryWrapper<GeoMonitorRecord>()
                .orderByDesc(GeoMonitorRecord::getMonitoredTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 3000))));
    }

    private List<String> parseUrls(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(json);
        }
    }

    private String scopeName(GeoQuery query, String dimension) {
        String value = switch (StringUtils.hasText(dimension) ? dimension : "hospital") {
            case "department" -> query.getDepartmentName();
            case "doctor" -> query.getDoctorName();
            default -> query.getHospitalName();
        };
        return StringUtils.hasText(value) ? value : "未归属";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static class ScoreAccumulator {
        private final String date;
        private final String scopeName;
        private int sum;
        private int count;

        private ScoreAccumulator(String date, String scopeName) {
            this.date = date;
            this.scopeName = scopeName;
        }

        private void add(int score) {
            sum += score;
            count++;
        }

        private Map<String, Object> toMap() {
            return Map.of("date", date, "scopeName", scopeName, "score", count == 0 ? 0 : Math.round((float) sum / count));
        }
    }

    private static class SourceAccumulator {
        private final String url;
        private final String host;
        private int count;
        private int official;
        private final Set<String> engines = new LinkedHashSet<>();
        private final Set<Long> queryIds = new LinkedHashSet<>();

        private SourceAccumulator(String url) {
            this.url = url;
            this.host = parseHost(url);
        }

        private void add(GeoMonitorRecord record, boolean officialSource) {
            count++;
            if (officialSource) {
                official++;
            }
            if (record.getEngine() != null) {
                engines.add(record.getEngine());
            }
            if (record.getQueryId() != null) {
                queryIds.add(record.getQueryId());
            }
        }

        private int getCount() {
            return count;
        }

        private Map<String, Object> toMap() {
            return Map.of(
                    "url", url,
                    "host", host,
                    "count", count,
                    "official", official > 0,
                    "engines", new ArrayList<>(engines),
                    "queryCount", queryIds.size()
            );
        }

        private static String parseHost(String url) {
            try {
                URI uri = URI.create(url);
                return uri.getHost() == null ? "" : uri.getHost();
            } catch (Exception e) {
                return "";
            }
        }
    }
}
