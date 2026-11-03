package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_message")
@Schema(
        description = "AI消息实体类，对应数据库表t_ai_message，存储AI对话中的单条消息信息（如发送角色、内容、关联会话及模型等）"
)
public class TAiMessage {

    private Long token;
    private String usageJson;

    @TableId(type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条消息记录）",
            example = "100001"
    )
    private Long id; // 主键ID（自增）

    /**
     * AI消息唯一标识
     * 对应数据库字段 ai_message_id
     */
    @TableField("ai_message_id")
    @Schema(
            description = "AI消息唯一业务标识（用于关联消息资源的业务主键，通常为bigint类型）",
            example = "200001"
    )
    private Long aiMessageId; // 通常使用Long对应数据库bigint类型

    /**
     * 关联的会话ID
     */
    @TableField("ai_conversation_id")
    @Schema(
            description = "关联的会话ID（关联到t_ai_conversation表的ai_conversation_id，标识该消息所属的对话）",
            example = "300001"
    )
    private Long aiConversationId;

    /**
     * 关联的模型配置ID
     */
    @TableField("ai_model_config_id")
    @Schema(
            description = "关联的AI模型配置ID（关联到模型配置表，标识该消息使用的AI模型）",
            example = "400001"
    )
    private Long aiModelConfigId;

    /**
     * 角色标识（例如：true=系统，false=用户）
     */
    @TableField("role")
    @Schema(
            description = "消息发送角色标识（true=系统/AI发送，false=用户发送）",
            example = "false",
            allowableValues = {"true", "false"}
    )
    private Boolean role;

    /**
     * 消息内容
     */
    @TableField("content")
    @Schema(
            description = "消息内容（用户的提问文本或AI的回答文本）",
            example = "如何给实体类添加Swagger注解？请提供示例。"
    )
    private String content;

    /**
     * 创建时间
     * 自动填充：插入时自动生成当前时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(
            description = "消息创建时间（数据库插入时自动填充当前时间，标识消息发送时刻）",
            example = "2024-09-01T10:30:00"
    )
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 自动填充：插入和更新时自动生成当前时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(
            description = "消息更新时间（数据库插入和更新时自动填充当前时间，标识消息最后修改时刻）",
            example = "2024-09-01T10:30:00"
    )
    private LocalDateTime updateTime;
}
