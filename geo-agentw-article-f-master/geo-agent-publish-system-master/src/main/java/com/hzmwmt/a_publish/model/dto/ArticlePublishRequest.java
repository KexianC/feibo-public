package com.hzmwmt.a_publish.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ArticlePublishRequest {

    private Long articleId;

    /**
     * 1: Zhihu, 2: Xiaohongshu, 3: Toutiao, 4: Baijiahao.
     */
    private Integer type;

    private String img;

    private List<String> tags;

    private String cookieFile;

    /**
     * Xiaohongshu uses this extra plain text field.
     */
    private String text;

    private String remark;
}
