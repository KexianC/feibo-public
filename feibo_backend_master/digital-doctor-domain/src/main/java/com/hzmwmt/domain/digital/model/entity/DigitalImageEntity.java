package com.hzmwmt.domain.digital.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalImageEntity
 * @Date: 2025/8/22 9:39
 */
@Data
@Builder
public class DigitalImageEntity {
    private Long digitalImageId;
    private String url;
    private String name;
    private Long userId;
    /**
     * 0-背景, 1-插图
     */
    private Boolean type;
}
