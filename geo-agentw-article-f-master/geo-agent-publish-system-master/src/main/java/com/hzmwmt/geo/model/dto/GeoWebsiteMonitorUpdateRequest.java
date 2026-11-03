package com.hzmwmt.geo.model.dto;

import lombok.Data;

/**
 * Mutable website-monitor settings. Null fields are left unchanged.
 * active and monitorEnabled accept only 0 or 1; monitorIntervalHours is 1-720.
 */
@Data
public class GeoWebsiteMonitorUpdateRequest {

    private String siteName;
    private Integer active;
    private Integer monitorEnabled;
    private Integer monitorIntervalHours;
}
