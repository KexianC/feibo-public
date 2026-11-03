package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoQueryGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoQuery;

import java.util.List;

public interface GeoQueryService {

    List<GeoQuery> generateQueries(GeoQueryGenerateRequest request);

    GeoQuery addManualQuery(GeoQuery query);

    GeoQuery updateQuery(Long id, GeoQuery query);

    IPage<GeoQuery> page(Page<GeoQuery> page, Long doctorId, Long departmentId, String disease, Integer enabled);

    List<GeoQuery> nextMonitorBatch(int limit, int staleHours);
}
