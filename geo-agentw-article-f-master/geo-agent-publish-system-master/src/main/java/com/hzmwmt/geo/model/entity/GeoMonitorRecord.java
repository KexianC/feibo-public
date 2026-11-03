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
@TableName(value = "geo_monitor_record", autoResultMap = true)
public class GeoMonitorRecord {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long queryId;

    private String engine;

    private String queryText;

    private String answerText;

    private Integer mentioned;

    private Integer cited;

    private String citedUrls;

    private String matchedTerms;

    private Integer visibilityScore;

    private Integer rankPosition;

    private Integer sourceCount;

    private Integer officialSourceCount;

    private String competitorMentions;

    private String rawPayload;

    private Long latencyMs;

    private Long articleId;

    private Integer status;

    private String errorMessage;

    private LocalDateTime monitoredTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
