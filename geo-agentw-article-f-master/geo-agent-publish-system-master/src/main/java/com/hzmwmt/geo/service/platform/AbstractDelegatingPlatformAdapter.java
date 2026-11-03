package com.hzmwmt.geo.service.platform;

import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.service.GeoEngineClient;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractDelegatingPlatformAdapter implements GeoPlatformAdapter {

    @Resource
    private GeoEngineClient geoEngineClient;

    private final String platformCode;
    private final Set<String> engineNames;
    private final Set<String> protocols;

    protected AbstractDelegatingPlatformAdapter(String platformCode, String[] engineNames, String[] protocols) {
        this.platformCode = platformCode;
        this.engineNames = normalize(engineNames);
        this.protocols = normalize(protocols);
    }

    @Override
    public String platformCode() {
        return platformCode;
    }

    @Override
    public boolean supports(GeoProperties.EngineProperties engine) {
        if (engine == null) {
            return false;
        }
        String name = normalize(engine.getName());
        String protocol = normalize(engine.getProtocol());
        return engineNames.contains(name) || protocols.contains(protocol);
    }

    @Override
    public GeoEngineAnswer ask(GeoProperties.EngineProperties engine, String queryText) {
        return geoEngineClient.ask(engine, queryText);
    }

    @Override
    public GeoEngineAnswer diagnose(GeoProperties.EngineProperties engine, String diagnosticPrompt) {
        return geoEngineClient.diagnose(engine, diagnosticPrompt);
    }

    private Set<String> normalize(String[] values) {
        return Arrays.stream(values == null ? new String[0] : values)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
