package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorCreateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorStatusResponse;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorUpdateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteReportResponse;
import com.hzmwmt.geo.model.entity.GeoWebsiteCheck;
import com.hzmwmt.geo.model.entity.GeoWebsiteMonitor;

public interface GeoWebsiteMonitorService {

    GeoWebsiteMonitorStatusResponse create(GeoWebsiteMonitorCreateRequest request);

    IPage<GeoWebsiteMonitorStatusResponse> page(Page<GeoWebsiteMonitor> page,
                                                Long hospitalId,
                                                Integer active,
                                                Integer monitorEnabled,
                                                Integer lastCheckStatus,
                                                String domain);

    GeoWebsiteMonitorStatusResponse update(Long id, GeoWebsiteMonitorUpdateRequest request);

    /**
     * Creates a manual check task. Implementations should return immediately;
     * the website fetch and audit run asynchronously.
     */
    GeoWebsiteCheck runCheck(Long id);

    IPage<GeoWebsiteCheck> checkPage(Page<GeoWebsiteCheck> page,
                                     Long monitorId,
                                     Integer status);

    GeoWebsiteReportResponse getLatestReport(Long id);
}
