package com.hzmwmt.domain.digital.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * 数字视频领域实体类
 *
 * @author huihui
 * @version v1.0
 * @ClassName DigitalVideoEntity
 * @Date: 2025/8/22 9:39
 */
@Data
@Builder
public class DigitalVideoEntity {
    private Long digitalVideoId;
    private String name;
    private Long userId;
    private Integer duration;
    private String url;
    /**
     * 状态字段：1表示成功，0表示失败
     */
    private Integer status;
}