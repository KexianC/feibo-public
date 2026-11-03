package com.hzmwmt.domain.message.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MessageEntity
 * @Date: 2025/9/11 23:44
 */
@Data
@Builder
public class MessageEntity {
    private Long messageId;
    private Long userId;
    private String title;
    private String content;
    private Date sendTime;
    private Date createTime;
    private Long token;
}
