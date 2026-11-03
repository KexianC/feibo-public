package com.hzmwmt.ai.rag.service;

import com.hzmwmt.ai.rag.model.entity.QdrantPoint;
import com.hzmwmt.ai.rag.model.entity.QdrantSearchHit;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;

import java.util.List;

/**
 * Qdrant REST 客户端服务。
 * 负责通过 HTTP 6333 端口完成集合检查、点位写入、向量检索和删除。
 */
public interface QdrantRestClient {

    /**
     * 获取当前集合名称。
     *
     * @return 集合名称
     */
    String collectionName();

    /**
     * 确保集合存在且向量维度正确。
     *
     * @param vectorSize 向量维度
     */
    void ensureCollection(int vectorSize);

    /**
     * 批量写入点位。
     *
     * @param points 点位列表
     */
    void upsert(List<QdrantPoint> points);

    /**
     * 按文档 ID 删除对应的全部向量点。
     *
     * @param fileId 文档 ID
     */
    void deleteByFileId(long fileId);

    /**
     * 执行向量检索。
     *
     * @param vector         查询向量
     * @param limit          返回条数
     * @param scoreThreshold 最小得分
     * @return 命中结果
     */
    List<QdrantSearchHit> search(KnowledgeAgentScope scope, List<Float> vector, int limit, double scoreThreshold);
}
