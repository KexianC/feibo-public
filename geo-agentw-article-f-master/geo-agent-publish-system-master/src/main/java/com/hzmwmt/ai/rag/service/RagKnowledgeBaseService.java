package com.hzmwmt.ai.rag.service;

import com.hzmwmt.ai.rag.model.dto.KnowledgeIngestResponse;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.model.dto.KnowledgeSearchHit;

import java.util.List;

/**
 * RAG 知识库服务。
 * 负责文档向量化、向量删除、知识检索。
 */
public interface RagKnowledgeBaseService {

    /**
     * 根据文档 ID 将已上传文件转化为向量并入库。
     *
     * @param documentId 知识库文档 ID
     * @return 导入结果
     */
    KnowledgeIngestResponse convertDocument(Long documentId);

    /**
     * 删除文档对应的向量数据，并移除知识库文档记录。
     *
     * @param documentId 知识库文档 ID
     */
    void deleteDocumentVector(Long documentId);

    /**
     * 按查询文本检索知识片段。
     *
     * @param query 查询内容
     * @param limit 返回条数
     * @return 检索命中结果
     */
    List<KnowledgeSearchHit> search(String query, Integer limit, Long doctorId);

    List<KnowledgeSearchHit> search(String query, Integer limit, KnowledgeAgentScope scope);
}
