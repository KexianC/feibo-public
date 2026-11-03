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
@TableName("geo_source_observation")
public class GeoSourceObservation {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long sourceAssetId;
    private String originType;
    private Long originId;
    private Long queryId;
    private String engine;
    private LocalDateTime observedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
