package com.hzmwmt.a_publish.model.dto;

import lombok.Data;

@Data
public class ArticleGenerateRequest {

    private Integer agentType;

    private Long hospitalId;

    private Long departmentId;

    private Long doctorId;

    private String prompt;

    private String coverUrl;

    /**
     * 0: draft, 1: pending review.
     */
    private Integer status;
}
