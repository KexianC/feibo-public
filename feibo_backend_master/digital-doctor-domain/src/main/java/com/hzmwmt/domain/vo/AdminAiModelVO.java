package com.hzmwmt.domain.vo;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AdminAiModelVO {
    /** String is intentional: model business IDs exceed JavaScript's safe integer range. */
    private String id;
    private String name;
    private String modelCode;
    private String modelName;
    /** String because provider business IDs exceed JavaScript's safe integer range. */
    private String providerId;
    private String providerName;
    private Integer providerOrder;
    private String description;
    private Boolean visible;
    private Boolean apiKeyConfigured;
    private String apiKeyMasked;
    private Integer order;
}
