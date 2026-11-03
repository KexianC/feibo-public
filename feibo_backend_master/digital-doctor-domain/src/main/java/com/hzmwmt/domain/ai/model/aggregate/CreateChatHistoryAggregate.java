package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CreateChatHistoryAggregate
 * @Date: 2025/9/11 14:53
 */
@Builder
@Data
public class CreateChatHistoryAggregate {
    private Long userId;
    private AIMessageEntity aiUserMessageEntity;
    private Boolean isNewConversation;
    //响应结果
    private String result;

}
