package com.hzmwmt.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;

@Data
public class AdminConversationMessageVO {
    private String id;
    private String role;
    private String content;
    private String model;
    private String modelId;
    private LocalDateTime createTime;
    private Long legacyEstimatedTokens;
    private ChatUsageRecord usage;
}
