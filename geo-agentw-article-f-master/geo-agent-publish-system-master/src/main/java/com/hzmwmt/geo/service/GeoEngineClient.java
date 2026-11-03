package com.hzmwmt.geo.service;

import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;

public interface GeoEngineClient {

    GeoEngineAnswer ask(GeoProperties.EngineProperties engine, String queryText);

    GeoEngineAnswer diagnose(GeoProperties.EngineProperties engine, String diagnosticPrompt);
}
