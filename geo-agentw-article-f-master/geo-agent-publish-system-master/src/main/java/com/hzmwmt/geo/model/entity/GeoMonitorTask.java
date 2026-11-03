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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "geo_monitor_task", autoResultMap = true)
public class GeoMonitorTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long queryId;

    /** Exact suggestion whose retest plan this task belongs to; null for legacy/general monitoring. */
    private Long feedbackSuggestionId;

    private Long retestBatchId;

    private String engine;

    private String queryText;

    private Integer priority;

    /**
     * 0-pending, 1-running, 2-success, 3-failed.
     */
    private Integer status;

    private String triggerType;

    private Integer attemptCount;

    private Integer maxAttempts;

    private LocalDateTime plannedTime;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime nextRetryTime;

    private Long monitorRecordId;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
