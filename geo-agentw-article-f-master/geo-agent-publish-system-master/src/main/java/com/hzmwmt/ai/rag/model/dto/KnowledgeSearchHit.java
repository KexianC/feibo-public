package com.hzmwmt.ai.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 知识检索命中结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchHit {

    /**
     * 向量记录 ID。
     */
    private String id;

    /**
     * 文档 ID。
     */
    private long fileId;

    /**
     * 1: hospital, 2: department, 3: doctor.
     */
    private Integer agentType;

    private Long hospitalId;

    private String hospitalName;

    private Long departmentId;

    private String departmentName;

    private Long doctorId;

    private String doctorName;

    /**
     * 相似度得分。
     */
    private double score;

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 当前片段在原文中的分块序号。
     */
    private Integer index;

    /**
     * 当前命中的文本内容。
     */
    private String text;

    /**
     * 除保留字段外的扩展元数据。
     */
    private Map<String, Object> metadata;
}
