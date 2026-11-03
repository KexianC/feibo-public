package com.hzmwmt.geo.service.platform;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.config.GeoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoPlatformRegistry {

    private final List<GeoPlatformAdapter> adapters;

    public GeoPlatformRegistry(List<GeoPlatformAdapter> adapters) {
        this.adapters = adapters;
    }

    public GeoPlatformAdapter resolve(GeoProperties.EngineProperties engine) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(engine))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        "GEO_PLATFORM_NOT_SUPPORTED",
                        "GEO monitor platform is not supported: " + (engine == null ? "" : engine.getName()),
                        HttpStatus.BAD_REQUEST
                ));
    }
}
