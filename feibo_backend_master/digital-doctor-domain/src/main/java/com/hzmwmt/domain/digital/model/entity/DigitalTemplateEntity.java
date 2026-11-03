package com.hzmwmt.domain.digital.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalTemplateEntity
 * @Date: 2025/9/24 20:50
 */
@Builder
@Data
public class DigitalTemplateEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 业务模板ID
     */
    private Long digitalTemplateId;

    /**
     * 模板标题
     */
    private String title;

    /**
     * 模板内容
     */
    private String body;

    /**
     * 创建时间
     */
    private Date createTime;

}
