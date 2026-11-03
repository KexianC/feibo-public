package com.hzmwmt.domain.ai.model.entity;

import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMessageEntity
 * @Date: 2025/8/26 1:01
 */
@Builder
@Data
public class AIMessageEntity {
    /**
     * 会话ID
     */
    private Long aiConversationId;
    /**
     * 业务消息ID
     */
    private Long aiMessageId;

    /**
     * 约定: false 代表 user (用户), true 代表 AIassistant (AI助手)
     */
    private AIChatRoleEnumVO aiChatRoleEnumVO;

    /**
     * 消息的具体内容
     */
    private String content;

    /**
     * 模型ID
     */
    private Long aiModelConfigId;

    /**
     * 创建时间
     */
    private Date createTime;

    private Long token;

    private Long aiToken;

    private ChatUsageRecord usageRecord;
}
