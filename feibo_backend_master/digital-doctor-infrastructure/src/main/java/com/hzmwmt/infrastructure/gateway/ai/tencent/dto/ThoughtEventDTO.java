package com.hzmwmt.infrastructure.gateway.ai.tencent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ThoughtEventDTO
 * @Date: 2025/9/24 17:01
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 JSON 中有但 DTO 中没有的字段，增强兼容性
public class ThoughtEventDTO {

    private String type;
    private ThoughtPayload payload;

    @JsonProperty("message_id")
    private String messageId;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThoughtPayload {
        private long elapsed;

        @JsonProperty("is_workflow")
        private boolean isWorkflow;

        private List<Procedure> procedures;

        @JsonProperty("session_id")
        private String sessionId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Procedure {
        private Debugging debugging;
        private String title;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Debugging {
        private String content;
    }
}