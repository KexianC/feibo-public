package com.hzmwmt.ai.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识导入完成后的响应对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestResponse {

    /**
     * 文档 ID。
     */
    private long fileId;

    /**
     * 本次导入的文档标题。
     */
    private String title;

    /**
     * 写入的 Qdrant 集合名称。
     */
    private String collectionName;

    /**
     * 本次导入后产生的分块数量。
     */
    private int chunkCount;
}
