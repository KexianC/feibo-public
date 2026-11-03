package com.hzmwmt.geo.service;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GeoEngineCatalog {

    @Resource
    private GeoProperties geoProperties;

    public List<GeoProperties.EngineProperties> enabledEngines(List<String> engineNames) {
        Set<String> filter = new LinkedHashSet<>();
        if (engineNames != null) {
            engineNames.stream()
                    .filter(StringUtils::hasText)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(filter::add);
        }
        return geoProperties.getEngines().stream()
                .filter(GeoProperties.EngineProperties::isEnabled)
                .filter(engine -> StringUtils.hasText(engine.getName()))
                .filter(engine -> filter.isEmpty() || filter.contains(engine.getName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public GeoProperties.EngineProperties enabledEngine(String engineName) {
        return geoProperties.getEngines().stream()
                .filter(GeoProperties.EngineProperties::isEnabled)
                .filter(engine -> StringUtils.hasText(engine.getName()))
                .filter(engine -> engine.getName().equalsIgnoreCase(engineName))
                .findFirst()
                .orElseThrow(() -> new AppException("GEO_ENGINE_NOT_FOUND", "GEO monitor engine is not enabled: " + engineName, HttpStatus.BAD_REQUEST));
    }
}
