package com.hzmwmt.domain.entitlement;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EntitlementGrantRequest {
    @NotBlank @Size(max = 100) private String requestId;
    @Pattern(regexp = "[1-9][0-9]{0,18}") private String planId;
    @Min(-1) @Max(36500) private Integer durationDays;
    @Min(-1) @Max(1000000) private Long premiumChatDailyLimit;
    @Min(0) @Max(1000000000) private Long digitalHumanSeconds;
    @Min(0) @Max(1000000000) private Long aiVideoSeconds;
    @Min(0) @Max(1000000000) private Long premiumChatCredits;
    @NotBlank @Size(max = 500) private String reason;
}
