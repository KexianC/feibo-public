package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.model.dto.GeoFeedbackGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;

import java.util.List;

public interface GeoFeedbackService {

    List<GeoFeedbackSuggestion> generateSuggestions(GeoFeedbackGenerateRequest request);

    IPage<GeoFeedbackSuggestion> page(Page<GeoFeedbackSuggestion> page, Long queryId, Long articleId, Integer status);
}
