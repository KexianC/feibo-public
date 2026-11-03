package com.hzmwmt.geo.job;

import com.hzmwmt.geo.service.GeoMonitorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeoMonitorJob {

    @Resource
    private GeoMonitorService geoMonitorService;

    @Scheduled(cron = "${app.geo.monitor-cron:0 0 */6 * * ?}")
    public void monitorGeoQueries() {
        try {
            int count = geoMonitorService.runScheduledBatch().size();
            log.info("GEO monitor job finished: recordCount={}", count);
        } catch (Exception e) {
            log.error("GEO monitor job failed: exceptionType={}", e.getClass().getSimpleName());
        }
    }
}
