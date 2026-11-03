package com.hzmwmt.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 相关配置。
 * 用于统一管理文本切块、检索参数以及 Qdrant REST 连接信息。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /**
     * 是否启用 RAG 检索增强。
     */
    private boolean enabled = true;

    /**
     * 单个文本分块的最大字符数。
     */
    private int chunkSize;

    /**
     * 分块之间的重叠字符数，降低切块边界导致的信息丢失。
     */
    private int chunkOverlap;

    /**
     * 单次检索最多返回的片段数。
     */
    private int topK;

    /**
     * 向量检索最小分数阈值。
     */
    private double scoreThreshold;

    /**
     * 注入给大模型的最大上下文字符数。
     */
    private int maxContextChars;

    /**
     * Qdrant REST 配置。
     */
    private QdrantProperties qdrant = new QdrantProperties();

    /**
     * Qdrant 配置项。
     */
    @Data
    public static class QdrantProperties {

        /**
         * Qdrant REST 地址，使用 HTTP API 端口 6333。
         */
        private String baseUrl;

        /**
         * Qdrant API Key。
         */
        private String apiKey;

        /**
         * 向量集合名称。
         */
        private String collectionName;

        /**
         * 向量距离算法。
         */
        private String distance;
    }
}
