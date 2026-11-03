package com.hzmwmt.ai.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 手动导入文本知识时使用的请求对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestTextRequest {

    /**
     * 文档标题或来源名称
     */
    private String title;

    /**
     * 需要写入知识库的原始文本内容
     */
    private String content;

    /**
     * 额外元数据，会一起写入向量库
     */
    private Map<String, Object> metadata;
}
