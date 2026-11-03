package com.hzmwmt.monitoring;

import java.util.List;

/** Only explicitly selected operational values may cross this response boundary. */
public record SystemMonitorSnapshot(String sampledAt, int refreshIntervalSeconds, List<Component> components) {
    public record Component(String id, String name, String status, String message, List<Metric> metrics) {}
    public record Metric(String key, String label, Object value, String unit, String note) {}
}
