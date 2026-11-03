package com.hzmwmt.domain.ai.model.entity;

import lombok.Data;
import java.math.BigDecimal;

/** Per-inference metering snapshot. Null means unknown, never free. */
@Data
public class ChatUsageRecord {
    private String provider;
    private String model;
    private String responseModel;
    private String requestedAt;
    private String usageSource = "NOT_REPORTED";
    private Long inputTokens;
    private Long outputTokens;
    private Long cachedTokens;
    private Long reasoningTokens;
    private Long totalTokens;
    private String costStatus = "USAGE_NOT_REPORTED";
    private BigDecimal estimatedCost;
    private String currency = "CNY";
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cachePrice;
    private String priceVersion;
    private String priceSource;
    private String priceNote;
}
