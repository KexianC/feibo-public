package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSourceAsset;

public interface GeoSourceAssetService {

    void capture(GeoQuery query, GeoMonitorRecord record, GeoEvidence evidence);

    void captureProbe(GeoQuery query, GeoDialogueProbe probe, GeoEvidence evidence);

    int rebuildFromMonitorRecords(int limit);

    IPage<GeoSourceAsset> page(Page<GeoSourceAsset> page,
                               String sourceType,
                               Integer official,
                               String entityType,
                               String domain);
}
