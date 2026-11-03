package com.hzmwmt.domain.ai.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 内容审核结果（领域实体）
 */
@Data
@Builder
public class AIContentReviewResultEntity {

    /**
     * 是否通过审核（无阻断性问题）
     */
    private Boolean pass;

    /**
     * 命中的敏感词列表
     */
    private List<String> sensitiveWords;

    /**
     * 错别字提示
     */
    private List<String> typos;

    /**
     * 科学性错误或风险提示
     */
    private List<String> scientificIssues;

    /**
     * AI 的整体建议
     */
    private String suggestion;
}

