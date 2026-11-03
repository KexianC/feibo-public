package com.hzmwmt.geo.service;

import com.hzmwmt.geo.model.dto.GeoFeedbackGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;

import java.util.List;

public interface GeoOptimizationService {

    List<GeoFeedbackSuggestion> generateSuggestions(GeoFeedbackGenerateRequest request);

    List<GeoFeedbackSuggestion> generateForMonitorRecord(GeoMonitorRecord record);
}
