package com.hzmwmt.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 医生信息表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "doctor_info", autoResultMap = true)
public class DoctorInfo {

    /**
     * 医生ID（非自增，需手动赋值）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 医生姓名
     */
    private String doctorName;

    /**
     * 医院ID
     */
    private Long hospitalId;

    /**
     * 医院名称
     */
    private String hospitalName;

    /**
     * 科室ID
     */
    private Long departmentId;

    /**
     * 科室名称
     */
    private String departmentName;

    /**
     * 职称ID
     */
    private Long titleId;

    /**
     * 职称名称
     */
    private String titleName;

    /**
     * 性别：0-未知，1-男，2-女
     */
    private Integer gender;

    /**
     * 医生头像URL
     */
    private String avatarUrl;

    /**
     * 专业特长
     */
    private String specialty;

    /**
     * 知识库状态：0-未上传，1-可用，2-存在异常
     */
    private Integer knowledgeStatus;

    /**
     * 启用状态：0-禁用，1-启用
     */
    private Integer enableStatus;

    /**
     * 备注
     */
    private String remark;

    @TableField(exist = false)
    private Long fileCount;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
