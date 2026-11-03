package com.hzmwmt.ai.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Qdrant REST 写入点位对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QdrantPoint {

    /**
     * 向量点 ID。
     */
    private Long id;

    /**
     * 向量数组。
     */
    private List<Float> vector;

    /**
     * 对应 payload。
     */
    private Map<String, Object> payload;
}
