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
 * Immutable evidence from one official website GEO check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "geo_website_check", autoResultMap = true)
public class GeoWebsiteCheck {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long monitorId;

    /** manual or scheduled. */
    private String triggerType;

    /** 0-pending, 1-running, 2-success, 3-failed. */
    private Integer status;

    private String requestedUrl;
    private String finalUrl;
    private Integer httpStatus;
    private String contentType;
    private String pageTitle;
    private Integer score;
    private String checksJson;
    private String recommendationsJson;
    private String contentHash;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime startedTime;
    private LocalDateTime checkedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
