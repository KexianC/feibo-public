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
@TableName(value = "geo_query", autoResultMap = true)
public class GeoQuery {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long doctorId;

    private String doctorName;

    private Long departmentId;

    private String departmentName;

    private Long hospitalId;

    private String hospitalName;

    private String region;

    private String disease;

    private String treatmentScenario;

    private String queryCluster;

    private String queryText;

    private String expectedTerms;

    private Integer sourceType;

    private Integer priority;

    private Integer monitorIntervalHours;

    private Integer enabled;

    private LocalDateTime lastMonitorTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
