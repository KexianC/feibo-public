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
@TableName(value = "department_info", autoResultMap = true)
public class DepartmentInfo {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long hospitalId;

    private String hospitalName;

    private String departmentName;

    private String departmentDesc;

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
