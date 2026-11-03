package com.hzmwmt.model.entity;

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
@TableName(value = "system_operation_log", autoResultMap = true)
public class SystemOperationLog {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private String role;

    private String moduleName;

    private String actionName;

    private String method;

    private String path;

    private Integer status;

    private Long durationMs;

    private String remoteAddr;

    private String details;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
