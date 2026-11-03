package com.hzmwmt.ai.rag.service;

import com.hzmwmt.ai.rag.model.entity.KnowledgeChunk;
import dev.langchain4j.data.document.Document;

import java.util.List;

/**
 * 文档切块服务。
 * 负责将解析后的 {@link Document} 按统一规则切分为可向量化的知识片段。
 */
public interface KnowledgeChunker {

    /**
     * 对文档进行切块。
     *
     * @param fileId   文档 ID
     * @param document 解析后的文档对象
     * @return 分块结果
     */
    List<KnowledgeChunk> split(long fileId, Document document);
}
