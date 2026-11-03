package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoMonitorRunRequest;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;

import java.util.List;
import java.util.Map;

public interface GeoMonitorService {

    List<GeoMonitorRecord> runMonitor(GeoMonitorRunRequest request);

    List<GeoMonitorRecord> runScheduledBatch();

    IPage<GeoMonitorRecord> page(Page<GeoMonitorRecord> page, Long queryId, String engine, Integer mentioned, Integer cited);

    IPage<GeoMonitorTask> pageTasks(Page<GeoMonitorTask> page, Integer status, String engine, String triggerType);

    Map<String, Object> platformMatrix(int limit);

    Map<String, Object> trend(String dimension, int days);

    Map<String, Object> sourceAnalysis(int limit);
}
