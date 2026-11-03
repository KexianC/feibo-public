package com.hzmwmt.ai.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档切块后的内部模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    /**
     * 文档 ID。
     */
    private long fileId;

    /**
     * 分块序号。
     */
    private int index;

    /**
     * 分块文本内容。
     */
    private String text;

    /**
     * 当前分块关联的元数据。
     */
    private Map<String, Object> metadata;
}
