package com.hzmwmt.geo.model.dto;

import com.hzmwmt.geo.model.entity.GeoWebsiteCheck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Latest deterministic website audit together with AI citation evidence.
 * Raw fetched HTML is intentionally never exposed by this response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoWebsiteReportResponse {

    private GeoWebsiteMonitorStatusResponse monitor;
    private GeoWebsiteCheck latestCheck;

    @Builder.Default
    private List<Map<String, Object>> checks = new ArrayList<>();

    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
}
