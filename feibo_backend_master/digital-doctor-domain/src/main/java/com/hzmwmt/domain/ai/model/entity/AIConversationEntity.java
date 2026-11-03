package com.hzmwmt.domain.ai.model.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIConversationEntity
 * @Date: 2025/8/26 1:01
 */
@Builder
@Data
public class AIConversationEntity {
    /**
     * 会话ID。如果是新对话，此字段可以为null
     */
    private Long aiConversationId;
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    private Date createTime;

    private Date updateTime;

    private Long consumption;

    private BigDecimal billing;

}
