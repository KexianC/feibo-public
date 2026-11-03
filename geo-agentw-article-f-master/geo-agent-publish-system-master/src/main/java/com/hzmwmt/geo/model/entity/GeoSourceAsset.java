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
@TableName(value = "geo_source_asset", autoResultMap = true)
public class GeoSourceAsset {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String urlHash;
    private String url;
    private String title;
    private String domain;
    private String sourceType;
    private String entityType;
    private Long entityId;
    private String entityName;
    private Integer official;
    private Integer authorityScore;
    private Integer citationCount;
    private String engines;
    private Integer verificationStatus;
    private String usageRecommendation;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
