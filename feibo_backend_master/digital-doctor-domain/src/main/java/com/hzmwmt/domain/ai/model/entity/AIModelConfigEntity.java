package com.hzmwmt.domain.ai.model.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIModelConfigEntity
 * @Date: 2025/9/20 16:12
 */
@Builder
@Data
public class AIModelConfigEntity {
    /**
     * 业务ID
     */
    private Long aiModelConfigId;

    /**
     * 用于发起请求的唯一名称
     */
    private String name;

    /**
     * 关于此配置的描述
     */
    private String description;

    /**
     * 模型类型 0: 通用模型 1: 高级模型
     */
    private Boolean type;

    /**
     * 模型代码
     */
    private String modelCode;

    /**
     * 模型图标
     */
    private String image;

    private BigDecimal pricePerThousandToken;
}
