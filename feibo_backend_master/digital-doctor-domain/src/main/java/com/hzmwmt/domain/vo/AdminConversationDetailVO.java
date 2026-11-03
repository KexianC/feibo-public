package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员端对话详情的视图对象（用于展示单条对话的完整信息）
 */
@Data
@Schema(description = "管理员端对话详情的视图对象，包含对话的完整信息（如ID、用户信息、所有交互消息、使用模型等）")
public class AdminConversationDetailVO {
    private String id;
    private String conversationId;
    private String userId;
    private String username;
    private String title;
    private String model;
    private Long tokens;
    private java.math.BigDecimal estimatedCost;
    private int meteredReplies;
    private int pricedReplies;
    private int totalReplies;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
    private java.util.List<AdminConversationMessageVO> messages;
}
