package com.hzmwmt.geo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoGapAnalysis {

    private String gapType;
    private String gapSummary;

    @Builder.Default
    private List<String> sourceRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> recommendedActions = new ArrayList<>();
}
