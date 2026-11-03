package com.hzmwmt.ai.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Qdrant REST 检索命中对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QdrantSearchHit {

    /**
     * 向量点 ID。
     */
    private String id;

    /**
     * 检索得分。
     */
    private double score;

    /**
     * payload 内容。
     */
    private Map<String, Object> payload;
}
