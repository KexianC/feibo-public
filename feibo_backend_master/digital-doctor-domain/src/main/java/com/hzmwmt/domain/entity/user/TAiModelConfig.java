package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("t_ai_model_config")
@Data
@Schema(
        description = "AI模型配置实体类，对应数据库表t_ai_model_config，存储AI模型的配置信息（如名称、分类、描述、模型编码等）"
)
public class TAiModelConfig {

    @TableField("id")
    @Schema(description = "数据库内部主键，不对外作为模型标识")
    private Long id;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(
            description = "AI模型配置主键ID（自增，唯一标识一条模型配置记录）",
            example = "1001"
    )
    private Long aiModelConfigId;

    @Schema(
            description = "父级模型配置ID（用于模型分类层级，0表示顶级节点，非0关联父级配置）",
            example = "0"
    )
    private Long parentId;

    @Schema(
            description = "是否为叶子节点（true=叶子节点，对应具体可用模型；false=非叶子节点，对应模型分类）",
            example = "true",
            allowableValues = {"true", "false"}
    )
    private Boolean isLeaf;

    @Schema(
            description = "模型配置名称（如模型分类名或具体模型名，如'GPT系列'、'文心一言'）",
            example = "GPT-4"
    )
    private String name;

    @Schema(description = "前台展示名称；为空时回退到供应商模型名称")
    private String displayName;

    @Schema(
            description = "模型展示图片URL（用于前端展示模型的图标或封面图）",
            example = "https://example.com/models/gpt4-icon.png"
    )
    @TableField("image")
    private String image;

    @Schema(
            description = "模型配置描述（说明模型的功能、特点、适用场景等）",
            example = "OpenAI推出的GPT-4模型，支持多轮对话和复杂任务处理，精度更高"
    )
    private String description;

    @Schema(description = "管理员可编辑的前台简介；与运行时系统提示词隔离")
    private String displayDescription;

    @Schema(
            description = "模型唯一编码（调用AI接口时使用的标识，如'gpt-4'、'ernie-bot'）",
            example = "gpt-4"
    )
    private String modelCode;

    @Schema(
            description = "额度分类（true=高级模型，false=普通模型）",
            example = "true",
            allowableValues = {"true", "false"}
    )
    private Boolean type;

    @Schema(
            description = "排序",
            example = "1"
    )
    private Integer sort;

    /** Read-only projection of the owning provider category business ID. */
    @TableField(exist = false)
    private Long providerId;

    /** Read-only projection of the owning provider category name. */
    @TableField(exist = false)
    private String providerName;

    /** Read-only projection of the owning provider category order. */
    @TableField(exist = false)
    private Integer providerOrder;

    @Schema(description = "是否在智笔医言前台显示")
    private Boolean visible;

    @Schema(description = "是否已从智笔医言模型管理中移除")
    private Boolean isDeleted;

    @Schema(description = "API Key 的 AES-GCM 密文，禁止通过接口序列化返回")
    @JsonIgnore
    @ToString.Exclude
    private String apiKeyCiphertext;

    @TableField(fill = FieldFill.INSERT)
    @Schema(
            description = "记录创建时间（数据库插入时自动填充当前时间）",
            example = "2024-09-10T09:00:00"
    )
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(
            description = "记录更新时间（数据库插入和更新时自动填充当前时间）",
            example = "2024-09-10T14:20:00"
    )
    private LocalDateTime updateTime;
}
