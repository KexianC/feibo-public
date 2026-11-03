package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryConversationResponseDTO
 * @Date: 2025/9/11 14:27
 */
@Builder
@Data
public class QueryConversationResponseDTO implements Serializable {

    /**
     * 会话ID
     */
    private Long aiConversationId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private String createTime;
}
