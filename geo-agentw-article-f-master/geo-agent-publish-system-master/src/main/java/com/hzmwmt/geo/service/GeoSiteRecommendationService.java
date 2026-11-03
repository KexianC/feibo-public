package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.dto.GeoSiteRecommendationStatusRequest;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSiteRecommendation;

public interface GeoSiteRecommendationService {

    GeoSiteRecommendation createFromMonitor(GeoQuery query,
                                            GeoMonitorRecord record,
                                            GeoFeedbackSuggestion suggestion);

    GeoSiteRecommendation createFromProbe(GeoQuery query,
                                          GeoDialogueProbe probe,
                                          GeoGapAnalysis analysis);

    IPage<GeoSiteRecommendation> page(Page<GeoSiteRecommendation> page,
                                      Integer status,
                                      String entityType,
                                      String recommendationType);

    GeoSiteRecommendation updateStatus(Long id, GeoSiteRecommendationStatusRequest request);
}
