package com.hzmwmt.infrastructure.gateway.ai.tencent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ErrorEventDTO
 * @Date: 2025/9/11 16:31
 */
@Data
public class ErrorEventDTO {
    /**
     * 事件类型, 固定为 "error"
     */
    private String type;

    /**
     * 请求 ID
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 错误信息核心
     */
    @JsonProperty("error")
    private ErrorPayload errorPayload;

    /**
     * 错误信息的数据结构
     */
    @Data
    public static class ErrorPayload {
        /**
         * 错误码
         */
        private int code;
        /**
         * 错误信息
         */
        private String message;
    }
}
