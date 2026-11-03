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
@TableName(value = "geo_feedback_suggestion", autoResultMap = true)
public class GeoFeedbackSuggestion {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long queryId;

    private Long monitorRecordId;

    private Long articleId;

    private String suggestionType;

    private String title;

    private String content;

    private String faqJson;

    private String structuredSummary;

    private String missReason;

    private String clusterKey;

    private Integer complianceStatus;

    private String complianceResult;

    private Integer publishStatus;

    private String targetChannels;

    private String executionMode;

    private String officialSiteRecommendation;

    private Integer retestStatus;

    private Long activeRetestBatchId;

    private String retestPlan;

    private LocalDateTime lastRetestTime;

    private Integer status;

    private String processingNote;

    private Long handledBy;

    private String manualPublishedUrl;

    private Long publicationConfirmedBy;

    private LocalDateTime publicationConfirmedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
