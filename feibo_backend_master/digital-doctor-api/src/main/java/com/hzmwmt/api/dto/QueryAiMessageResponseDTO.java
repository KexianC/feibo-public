package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryAiMessageResponseDTO
 * @Date: 2025/9/11 14:30
 */
@Builder
@Data
public class QueryAiMessageResponseDTO implements Serializable {
    /**
     * 约定: false 代表 user (用户), true 代表 AIassistant (AI助手)
     */
    private Boolean role;

    /**
     * 消息的具体内容
     */
    private String content;

    private String model;
    /**
     * 创建时间
     */
    private String createTime;
}
