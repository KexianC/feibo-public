package com.hzmwmt.a_publish.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostArticleDTO {
    private static final long serialVersionUID = 1L;
    /**
     * 平台类型
     */
    private Integer type;
    /**
     * 标题
     */
    private String title;
    /**
     * 正文
     */
    private String content;
    /**
     * 封面
     */
    private String img;
    /**
     * 标签
     */
    private List<String> tags;
    /**
     * cookie文件名
     */
    private String cookie_file;
    /**
     * 小红书专用Text
     */
    private String text;
}
