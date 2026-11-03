package com.hzmwmt.a_publish.model.dto;

import lombok.Data;

@Data
public class ArticleAuditRequest {

    private Long articleId;

    /**
     * 2: approved, 4: rejected.
     */
    private Integer status;

    private String reason;
}
