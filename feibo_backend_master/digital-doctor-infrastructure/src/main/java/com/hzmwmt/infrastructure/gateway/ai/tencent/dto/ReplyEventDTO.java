package com.hzmwmt.infrastructure.gateway.ai.tencent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ReplyEventDTO
 * @Date: 2025/9/11 16:30
 */
@Data
public class ReplyEventDTO {
    /**
     * 事件类型, 固定为 "reply"
     */
    private String type;

    /**
     * 事件的核心数据
     */
    private ReplyPayload payload;

    /**
     * SSE "reply" 事件的 Payload DTO
     */
    @Data
    public static class ReplyPayload {
        /**
         * 请求 ID
         */
        @JsonProperty("request_id")
        private String requestId;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 文件信息
         */
        @JsonProperty("file_infos")
        private List<FileInfo> fileInfos;

        /**
         * 消息唯一 ID
         */
        @JsonProperty("record_id")
        private String recordId;

        /**
         * 关联的消息唯一 ID
         */
        @JsonProperty("related_record_id")
        private String relatedRecordId;

        /**
         * 会话 ID
         */
        @JsonProperty("session_id")
        private String sessionId;

        /**
         * 消息是否由自己发出
         */
        @JsonProperty("is_from_self")
        private boolean isFromSelf;

        /**
         * 该消息记录是否能评价
         */
        @JsonProperty("can_rating")
        private boolean canRating;

        /**
         * 消息时间戳（秒级）
         */
        private long timestamp;

        /**
         * reply 事件消息是否已输出完
         */
        @JsonProperty("is_final")
        private boolean isFinal;

        /**
         * 是否命中敏感内容
         */
        @JsonProperty("is_evil")
        private boolean isEvil;

        /**
         * 是否为模型生成内容
         */
        @JsonProperty("is_llm_generated")
        private boolean isLlmGenerated;

        /**
         * 回复方式
         */
        @JsonProperty("reply_method")
        private int replyMethod;

        /**
         * 命中的知识
         */
        private List<Knowledge> knowledge;

        /**
         * 选项卡，任务流程专有
         */
        @JsonProperty("option_cards")
        private List<String> optionCards;

        /**
         * 用户自定义业务参数
         */
        @JsonProperty("custom_params")
        private List<String> customParams;

        /**
         * 任务流程调试信息
         */
        @JsonProperty("task_flow")
        private Object taskFlow;

        /**
         * 工作流调试信息
         */
        @JsonProperty("work_flow")
        private WorkflowDebugInfo workFlow;

        /**
         * 引用信息
         */
        @JsonProperty("quote_infos")
        private List<QuoteInfo> quoteInfos;
    }

    // --- 内嵌的辅助 DTO ---

    /**
     * 文件信息的数据结构
     */
    @Data
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
     * 命中的知识的数据结构
     */
    @Data
    public static class Knowledge {
        private String id;
        private int type;
        @JsonProperty("seg_id")
        private String segId;
    }

    /**
     * 引用信息数据结构
     */
    @Data
    public static class QuoteInfo {
        private int index;
        private int position;
    }

    /**
     * 工作流调试信息的数据结构
     */
    @Data
    public static class WorkflowDebugInfo {
        @JsonProperty("workflow_name")
        private String workflowName;
        @JsonProperty("workflow_id")
        private String workflowId;
        @JsonProperty("workflow_run_id")
        private String workflowRunId;
        @JsonProperty("option_cards")
        private List<String> optionCards;
        @JsonProperty("current_node")
        private CurrentNode currentNode;
        private List<String> outputs;
    }

    /**
     * 当前工作流节点信息
     */
    @Data
    public static class CurrentNode {
        @JsonProperty("NodeID")
        private String nodeId;
        @JsonProperty("NodeType")
        private int nodeType;
        @JsonProperty("NodeName")
        private String nodeName;
        @JsonProperty("Status")
        private int status;
        @JsonProperty("Input")
        private String input;
        @JsonProperty("Output")
        private String output;
        @JsonProperty("TaskOutput")
        private String taskOutput;
        @JsonProperty("FailMessage")
        private String failMessage;
        @JsonProperty("CostMilliSeconds")
        private String costMilliSeconds;
        @JsonProperty("Reply")
        private String reply;
        @JsonProperty("BelongNodeID")
        private String belongNodeId;
        @JsonProperty("StatisticInfo")
        private StatisticInfo statisticInfo;
    }

    /**
     * LLM 统计信息
     */
    @Data
    public static class StatisticInfo {
        @JsonProperty("ModelName")
        private String modelName;
        @JsonProperty("FirstTokenCost")
        private int firstTokenCost;
        @JsonProperty("TotalCost")
        private int totalCost;
        @JsonProperty("InputTokens")
        private int inputTokens;
        @JsonProperty("OutputTokens")
        private int outputTokens;
        @JsonProperty("TotalTokens")
        private int totalTokens;
    }
}
