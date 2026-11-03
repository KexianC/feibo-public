package com.hzmwmt.domain.entitlement;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class EntitlementProductRequest {
    @NotBlank @Size(max = 64) private String name;
    @NotNull @DecimalMin("0.00") @DecimalMax("1000000") private BigDecimal price;
    @NotNull @Min(-1) @Max(36500) private Integer durationDays;
    @NotNull private Boolean active;
    @NotNull @Min(-1) @Max(1000000) private Long premiumChatDailyLimit;
    @NotNull @Min(0) @Max(1000000000) private Long digitalHumanSeconds;
    @NotNull @Min(0) @Max(1000000000) private Long aiVideoSeconds;
    @NotNull @Min(0) @Max(1000000000) private Long premiumChatCredits;
}
