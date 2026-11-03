package com.hzmwmt.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "hospital_info", autoResultMap = true)
public class HospitalInfo {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String hospitalName;

    private String hospitalLevel;

    private String hospitalType;

    private String province;

    private String city;

    private String district;

    private String address;

    private String phone;

    private String logoUrl;

    private String description;

    private Integer enableStatus;

    @TableField(exist = false)
    private Long fileCount;

    @TableField(exist = false)
    private Integer knowledgeStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
