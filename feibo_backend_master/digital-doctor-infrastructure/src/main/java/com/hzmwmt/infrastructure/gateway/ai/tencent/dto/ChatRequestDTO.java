package com.hzmwmt.infrastructure.gateway.ai.tencent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ChatRequestDTO
 * @Date: 2025/9/11 16:01
 */
@Data
@Builder

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequestDTO {

    /**
     * 请求 ID，用于标识一个请求。
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 消息内容。
     */
    @JsonProperty("content")
    private String content;

    /**
     * 文件信息列表。
     */
    @JsonProperty("file_infos")
    private List<FileInfo> fileInfos;

    /**
     * 会话 ID。
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 应用密钥。
     */
    @JsonProperty("bot_app_key")
    private String botAppKey;

    /**
     * 访客 ID。
     */
    @JsonProperty("visitor_biz_id")
    private String visitorBizId;

    /**
     * 流式回复频率控制。
     */
    @JsonProperty("streaming_throttle")
    private Integer streamingThrottle;

    /**
     * 自定义参数。
     */
    @JsonProperty("custom_variables")
    private Map<String, String> customVariables;

    /**
     * 角色指令（提示词）。
     */
    @JsonProperty("system_role")
    private String systemRole;

    /**
     * 控制 content 是否是增量输出。
     */
    @JsonProperty("incremental")
    private Boolean incremental;

    /**
     * 是否开启联网搜索 (enable/disable/"")。
     */
    @JsonProperty("search_network")
    private String searchNetwork;

    /**
     * 指定模型名称。
     */
    @JsonProperty("model_name")
    private String modelName;

    /**
     * 是否开启流式传输 (enable/disable/"")。
     */
    @Builder.Default
    @JsonProperty("stream")
    private String stream = "enable";

    /**
     * 是否开启工作流 (enable/disable/"")。
     */
    @JsonProperty("workflow_status")
    private String workflowStatus;

    /**
     * 知识标签列表。
     */
    @JsonProperty("visitor_labels")
    private List<VisitorLabel> visitorLabels;

    // --- 内部静态类定义 ---

    /**
     * 文件信息的数据结构 (作为内部类)
     */
    @Data
    @Builder
    public static class FileInfo {
        @JsonProperty("file_name")
        private String fileName;
        @JsonProperty("file_size")
        private String fileSize;
        @JsonProperty("file_url")
        private String fileUrl;
        @JsonProperty("file_type")
        private String fileType;
        @JsonProperty("doc_id")
        private String docId;
    }

    /**
     * 知识标签列表的数据结构 (作为内部类)
     */
    @Data
    @Builder
    public static class VisitorLabel {
        private String name;
        private List<String> values;
    }
}
