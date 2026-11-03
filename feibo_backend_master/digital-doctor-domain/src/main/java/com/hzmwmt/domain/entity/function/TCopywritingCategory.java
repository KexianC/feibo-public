package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文案分类实体类
 * 对应数据库表：t_copywriting_category
 */
@Data
@TableName("t_copywriting_category")
@Schema(
        name = "TCopywritingCategory",
        description = "文案分类实体，存储文案分类的基本信息（如名称、状态等）"
)
public class TCopywritingCategory {

    @Schema(
            description = "主键ID（自增策略）",
            example = "1001",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(
            description = "脚本分类id",
            example = "1965701933805572098"
    )
    private Long copywritingCategoryId;

    @Schema(
            description = "分类名称（如'产品宣传文案'、'活动推广文案'）",
            example = "产品宣传文案"
    )
    @TableField("name")
    private String name;


    @Schema(
            description = "分类状态（true=启用，false=禁用）",
            example = "true",
            allowableValues = {"true", "false"}
    )
    @TableField("status")
    private Boolean status;

    @Schema(
            description = "创建时间（自动填充，插入时生成）",
            example = "2024-10-16T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(
            description = "更新时间（自动填充，插入和更新时生成）",
            example = "2024-10-16T15:45:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}