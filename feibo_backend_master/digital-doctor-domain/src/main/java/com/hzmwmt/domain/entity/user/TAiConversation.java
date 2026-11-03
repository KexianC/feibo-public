package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话实体类
 */
@Data
@TableName("t_ai_conversation")
@Schema(
        description = "AI对话实体类，对应数据库表t_ai_conversation，存储AI对话的基础信息（如用户ID、对话标题、创建时间等）"
)
public class TAiConversation {

    /**
     * 主键 ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条AI对话记录）",
            example = "1001"
    )
    private Integer id;

    /**
     * AI 对话关联 ID
     */
    @TableField("ai_conversation_id")
    @Schema(
            description = "AI对话关联ID（关联AI对话资源的业务唯一标识）",
            example = "100001"
    )
    private Long aiConversationId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    @Schema(
            description = "用户ID（关联发起AI对话的用户）",
            example = "10086"
    )
    private Long userId;

    /**
     * 对话标题 就是问题
     */
    @TableField("title")
    @Schema(
            description = "对话标题（取自对话中的首次提问内容，作为对话的标识性标题）",
            example = "如何给实体类添加Swagger注解？"
    )
    private String title;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(
            description = "对话创建时间（数据库自动生成，标识对话发起时刻）",
            example = "2024-07-01T09:15:00"
    )
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(
            description = "对话最后更新时间（对话内容变更时自动更新，标识最后一条消息的发送时刻）",
            example = "2024-07-01T09:20:30"
    )
    private LocalDateTime updateTime;
}