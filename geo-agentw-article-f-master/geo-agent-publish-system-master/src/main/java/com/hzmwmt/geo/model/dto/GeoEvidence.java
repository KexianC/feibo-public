package com.hzmwmt.geo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoEvidence {

    private String answerText;

    @Builder.Default
    private List<String> citedUrls = new ArrayList<>();

    @Builder.Default
    private List<String> matchedTerms = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> sourceDetails = new ArrayList<>();

    private Integer rankPosition;

    private int sourceCount;

    private int officialSourceCount;

    @Builder.Default
    private List<String> competitorMentions = new ArrayList<>();

    private String rawPayload;

    private Long latencyMs;
}
