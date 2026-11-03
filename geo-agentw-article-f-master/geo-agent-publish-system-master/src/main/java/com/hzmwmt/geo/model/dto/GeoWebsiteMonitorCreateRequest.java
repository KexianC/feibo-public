package com.hzmwmt.geo.model.dto;

import lombok.Data;

/**
 * Request to register an official website for monitoring.
 * The service validates that hospitalId and siteUrl are present, that the URL
 * is a safe public HTTP(S) target, and that monitorIntervalHours is 1-720.
 */
@Data
public class GeoWebsiteMonitorCreateRequest {

    private Long hospitalId;
    private String siteName;
    private String siteUrl;
    private Integer monitorIntervalHours = 24;
    private Boolean runImmediately = Boolean.TRUE;
}
