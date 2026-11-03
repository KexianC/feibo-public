package com.hzmwmt.geo.service.platform;

import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;

public interface GeoPlatformAdapter {

    String platformCode();

    boolean supports(GeoProperties.EngineProperties engine);

    GeoEngineAnswer ask(GeoProperties.EngineProperties engine, String queryText);

    GeoEngineAnswer diagnose(GeoProperties.EngineProperties engine, String diagnosticPrompt);
}
