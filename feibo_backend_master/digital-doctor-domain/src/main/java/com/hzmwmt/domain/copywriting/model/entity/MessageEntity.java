package com.hzmwmt.domain.copywriting.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MessageEntity
 * @Date: 2025/9/7 17:06
 */
@Builder
@Data
public class MessageEntity {
    private Long userId;
    private String title;
    private String content;
    private Date createTime;
}
