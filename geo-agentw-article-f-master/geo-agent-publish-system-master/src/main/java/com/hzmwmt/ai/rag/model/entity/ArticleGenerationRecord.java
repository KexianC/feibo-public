package com.hzmwmt.ai.rag.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章生成对话记录表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "article_generation_record", autoResultMap = true)
public class ArticleGenerationRecord {

    /**
     * 记录ID（雪花ID，手动赋值）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 1: hospital, 2: department, 3: doctor.
     */
    private Integer agentType;

    private Long hospitalId;

    private String hospitalName;

    private Long departmentId;

    private String departmentName;

    /**
     * 医生ID（操作者）
     */
    private Long doctorId;

    private String doctorName;

    /**
     * 用户输入的问题/指令
     */
    private String queryText;

    /**
     * 用到的知识库来源（如检索到的文件名列表，JSON格式）
     */
    private String knowledgeSources;

    /**
     * 模型返回的文章标题
     */
    private String responseTitle;

    /**
     * 模型返回的文章正文
     */
    private String responseContent;

    /**
     * 关联的文章ID（保存后回填）
     */
    private Long articleId;

    /**
     * 对话状态：1-成功，2-失败
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
