package com.hzmwmt.geo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Website monitor summary for list and status views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoWebsiteMonitorStatusResponse {

    private Long id;
    private Long hospitalId;
    private String hospitalName;
    private String siteName;
    private String siteUrl;
    private String normalizedUrl;
    private String domain;
    private Integer active;
    private Integer monitorEnabled;
    private Integer monitorIntervalHours;
    private Integer lastCheckStatus;
    private Integer lastScore;
    private Integer lastHttpStatus;
    private String lastFinalUrl;
    private String lastError;
    private LocalDateTime lastCheckedTime;
    private LocalDateTime nextCheckTime;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private Long latestCheckId;
    private Long citationCount;
    private Long citingQueryCount;

    @Builder.Default
    private List<String> citingEngines = new ArrayList<>();

    private LocalDateTime lastCitationTime;
}
