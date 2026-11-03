package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoWebsiteCheckMapper;
import com.hzmwmt.geo.mapper.GeoWebsiteMonitorMapper;
import com.hzmwmt.geo.model.entity.GeoWebsiteCheck;
import com.hzmwmt.geo.model.entity.GeoWebsiteMonitor;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class GeoWebsiteMonitorWorker {

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoWebsiteMonitorMapper websiteMonitorMapper;

    @Resource
    private GeoWebsiteCheckMapper websiteCheckMapper;

    @Resource
    private GeoWebsiteHttpFetcher httpFetcher;

    @Resource
    private GeoWebsiteAuditScorer auditScorer;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(Math.max(1, geoProperties.getWebsiteWorkerConcurrency()));
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${app.geo.website-worker-poll-ms:15000}")
    public void poll() {
        if (!geoProperties.isWebsiteMonitorEnabled()) {
            return;
        }
        try {
            scheduleDueMonitors();
            processPending();
        } catch (Exception e) {
            log.warn("Website GEO worker polling failed: exceptionType={}", e.getClass().getSimpleName());
        }
    }

    public void processAsync(GeoWebsiteCheck check) {
        if (check != null && check.getId() != null) {
            CompletableFuture.runAsync(() -> claimAndRun(check.getId()), executorService);
        }
    }

    void scheduleDueMonitors() {
        LocalDateTime now = LocalDateTime.now();
        int limit = Math.max(1, Math.min(50, geoProperties.getWebsiteWorkerBatchSize()));
        List<GeoWebsiteMonitor> due = websiteMonitorMapper.selectList(new LambdaQueryWrapper<GeoWebsiteMonitor>()
                .eq(GeoWebsiteMonitor::getActive, 1)
                .eq(GeoWebsiteMonitor::getMonitorEnabled, 1)
                .le(GeoWebsiteMonitor::getNextCheckTime, now)
                .orderByAsc(GeoWebsiteMonitor::getNextCheckTime)
                .last("LIMIT " + limit));
        for (GeoWebsiteMonitor monitor : due) {
            int interval = normalizeInterval(monitor.getMonitorIntervalHours());
            int claimed = websiteMonitorMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                    .eq(GeoWebsiteMonitor::getId, monitor.getId())
                    .eq(GeoWebsiteMonitor::getActive, 1)
                    .eq(GeoWebsiteMonitor::getMonitorEnabled, 1)
                    .le(GeoWebsiteMonitor::getNextCheckTime, now)
                    .set(GeoWebsiteMonitor::getNextCheckTime, now.plusHours(interval)));
            if (claimed == 1) {
                websiteCheckMapper.insert(newPendingCheck(monitor, "scheduled"));
            }
        }
    }

    void processPending() {
        int limit = Math.max(1, Math.min(50, geoProperties.getWebsiteWorkerBatchSize()));
        List<GeoWebsiteCheck> pending = websiteCheckMapper.selectList(new LambdaQueryWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getStatus, 0)
                .orderByAsc(GeoWebsiteCheck::getCreatedTime)
                .last("LIMIT " + limit));
        pending.forEach(this::processAsync);
    }

    private void claimAndRun(Long checkId) {
        LocalDateTime started = LocalDateTime.now();
        int claimed = websiteCheckMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getId, checkId)
                .eq(GeoWebsiteCheck::getStatus, 0)
                .set(GeoWebsiteCheck::getStatus, 1)
                .set(GeoWebsiteCheck::getStartedTime, started));
        if (claimed != 1) {
            return;
        }
        GeoWebsiteCheck check = websiteCheckMapper.selectById(checkId);
        GeoWebsiteMonitor monitor = check == null ? null : websiteMonitorMapper.selectById(check.getMonitorId());
        if (check == null || monitor == null || !Integer.valueOf(1).equals(monitor.getActive())) {
            fail(checkId, monitor, "官网监测项不存在或已停用", started);
            return;
        }
        websiteMonitorMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                .eq(GeoWebsiteMonitor::getId, monitor.getId())
                .set(GeoWebsiteMonitor::getLastCheckStatus, 1)
                .set(GeoWebsiteMonitor::getLastError, null));
        try {
            GeoWebsiteHttpFetcher.FetchResult homepage = httpFetcher.fetchHtml(check.getRequestedUrl());
            URI finalUri = URI.create(homepage.finalUrl());
            String origin = new URI(finalUri.getScheme(), null, finalUri.getHost(), -1, "/", null, null).toString();
            GeoWebsiteHttpFetcher.FetchResult robots = fetchOptional(origin + "robots.txt");
            GeoWebsiteHttpFetcher.FetchResult sitemap = fetchOptional(origin + "sitemap.xml");
            GeoWebsiteAuditScorer.AuditResult audit = auditScorer.score(homepage, robots, sitemap);
            LocalDateTime checkedTime = LocalDateTime.now();

            websiteCheckMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteCheck>()
                    .eq(GeoWebsiteCheck::getId, checkId)
                    .eq(GeoWebsiteCheck::getStatus, 1)
                    .set(GeoWebsiteCheck::getStatus, 2)
                    .set(GeoWebsiteCheck::getFinalUrl, truncate(homepage.finalUrl(), 2000))
                    .set(GeoWebsiteCheck::getHttpStatus, homepage.status())
                    .set(GeoWebsiteCheck::getContentType, truncate(homepage.contentType(), 255))
                    .set(GeoWebsiteCheck::getPageTitle, truncate(audit.pageTitle(), 500))
                    .set(GeoWebsiteCheck::getScore, audit.score())
                    .set(GeoWebsiteCheck::getChecksJson, objectMapper.writeValueAsString(audit.checks()))
                    .set(GeoWebsiteCheck::getRecommendationsJson, objectMapper.writeValueAsString(audit.recommendations()))
                    .set(GeoWebsiteCheck::getContentHash, audit.contentHash())
                    .set(GeoWebsiteCheck::getLatencyMs, homepage.latencyMs())
                    .set(GeoWebsiteCheck::getErrorMessage, null)
                    .set(GeoWebsiteCheck::getCheckedTime, checkedTime));
            websiteMonitorMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                    .eq(GeoWebsiteMonitor::getId, monitor.getId())
                    .set(GeoWebsiteMonitor::getLastCheckStatus, 2)
                    .set(GeoWebsiteMonitor::getLastScore, audit.score())
                    .set(GeoWebsiteMonitor::getLastHttpStatus, homepage.status())
                    .set(GeoWebsiteMonitor::getLastFinalUrl, truncate(homepage.finalUrl(), 2000))
                    .set(GeoWebsiteMonitor::getLastError, null)
                    .set(GeoWebsiteMonitor::getLastCheckedTime, checkedTime));
        } catch (Exception e) {
            String message = e instanceof AppException && e.getMessage() != null
                    ? e.getMessage()
                    : "官网检测失败，请稍后重试";
            fail(checkId, monitor, message, started);
            log.warn("Website GEO check failed: monitorId={}, exceptionType={}",
                    monitor.getId(), e.getClass().getSimpleName());
        }
    }

    private GeoWebsiteHttpFetcher.FetchResult fetchOptional(String url) {
        try {
            return httpFetcher.fetchText(url);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fail(Long checkId, GeoWebsiteMonitor monitor, String message, LocalDateTime started) {
        LocalDateTime checkedTime = LocalDateTime.now();
        String safeMessage = truncate(message, 1000);
        websiteCheckMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getId, checkId)
                .set(GeoWebsiteCheck::getStatus, 3)
                .set(GeoWebsiteCheck::getLatencyMs, Math.max(0, java.time.Duration.between(started, checkedTime).toMillis()))
                .set(GeoWebsiteCheck::getErrorMessage, safeMessage)
                .set(GeoWebsiteCheck::getCheckedTime, checkedTime));
        if (monitor != null) {
            websiteMonitorMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                    .eq(GeoWebsiteMonitor::getId, monitor.getId())
                    .set(GeoWebsiteMonitor::getLastCheckStatus, 3)
                    .set(GeoWebsiteMonitor::getLastError, safeMessage)
                    .set(GeoWebsiteMonitor::getLastCheckedTime, checkedTime));
        }
    }

    public GeoWebsiteCheck newPendingCheck(GeoWebsiteMonitor monitor, String triggerType) {
        return GeoWebsiteCheck.builder()
                .id(snowflakeIdGenerator.nextId())
                .monitorId(monitor.getId())
                .triggerType(triggerType)
                .status(0)
                .requestedUrl(monitor.getNormalizedUrl())
                .build();
    }

    private int normalizeInterval(Integer value) {
        return Math.max(1, Math.min(720, value == null ? 24 : value));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
