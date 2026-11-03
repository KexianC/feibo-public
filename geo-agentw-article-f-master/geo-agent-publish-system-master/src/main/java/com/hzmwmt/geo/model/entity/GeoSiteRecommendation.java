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
@TableName(value = "geo_site_recommendation", autoResultMap = true)
public class GeoSiteRecommendation {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long queryId;
    private Long probeId;
    private Long monitorRecordId;
    private String entityType;
    private Long entityId;
    private String entityName;
    private String recommendationType;
    private String title;
    private String currentGap;
    private String recommendedChange;
    private String contentBrief;
    private String evidenceJson;
    private String executionMode;
    private String externalOwner;
    private Integer priority;
    private Integer complianceStatus;
    private String complianceResult;
    private Integer status;
    private String statusNote;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
