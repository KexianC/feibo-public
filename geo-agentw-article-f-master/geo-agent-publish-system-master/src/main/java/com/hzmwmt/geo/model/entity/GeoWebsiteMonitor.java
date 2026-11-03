package com.hzmwmt.geo.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configuration and latest status for an official website GEO monitor.
 * Detailed, immutable scan results are stored in {@link GeoWebsiteCheck}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "geo_website_monitor", autoResultMap = true)
public class GeoWebsiteMonitor {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long hospitalId;
    private String hospitalName;
    private String siteName;
    private String siteUrl;
    private String normalizedUrl;
    private String urlHash;
    private String domain;

    /** 0-archived, 1-active. */
    private Integer active;

    /** 0-manual checks only, 1-scheduled monitoring enabled. */
    private Integer monitorEnabled;

    private Integer monitorIntervalHours;

    /** 0-pending/not checked, 1-running, 2-success, 3-failed. */
    private Integer lastCheckStatus;

    private Integer lastScore;
    private Integer lastHttpStatus;
    private String lastFinalUrl;
    private String lastError;
    private LocalDateTime lastCheckedTime;
    private LocalDateTime nextCheckTime;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
