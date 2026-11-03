package com.hzmwmt.a_publish.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 科普文章表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "article_info", autoResultMap = true)
public class ArticleInfo {

    /**
     * 文章ID（雪花ID，手动赋值）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Integer agentType;

    private Long hospitalId;

    private Long departmentId;

    /**
     * 医生ID（作者）
     */
    private Long doctorId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章正文
     */
    private String content;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 文章状态：0-草稿，1-待审核，2-审核通过（已定稿），3-已发布，4-审核不通过
     */
    private Integer status;

    /**
     * 正文字数
     */
    private Integer wordCount;

    /**
     * 备注
     */
    private String remark;

    private String sources;

    private String citations;

    private String medicalDisclaimer;

    private String schemaJson;

    private String statusAudit;

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
