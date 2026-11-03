package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIModelConfig
 * @Date: 2025/9/20 15:53
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_model_config")
public class AIModelConfig {

    /**
     * 主键ID，数据库自增
     */
    private Long id;


    /**
     * 业务ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiModelConfigId;

    /**
     * 父节点ID，0为根节点
     */
    private Long parentId;


    /**
     * 是否叶节点：0-否，1-是
     */
    private Boolean isLeaf;

    /**
     * 用于发起请求的唯一名称
     */
    private String name;

    /** User-facing name; null falls back to {@link #name}. */
    private String displayName;

    /**
     * 关于此配置的描述
     */
    private String description;

    /** Administrator-editable display copy; never used as a system prompt. */
    private String displayDescription;

    /**
     * 模型类型 0: 通用模型 1: 高级模型
     */
    private Boolean type;

    /**
     * 模型编码
     */
    private String modelCode;

    /**
     * 模型图标
     */
    private String image;

    private Integer sort;

    private Boolean visible;

    private Boolean isDeleted;

    @ToString.Exclude
    @JsonIgnore
    private String apiKeyCiphertext;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最后更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

}
