package com.hzmwmt.domain.entitlement;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class FreeEntitlementPolicy {
    @NotNull @Min(-1) @Max(1000000) private Long premiumChatDailyLimit;
    @NotNull @Min(0) @Max(1000000000) private Long digitalHumanSeconds;
    @NotNull @Min(0) @Max(1000000000) private Long aiVideoSeconds;
}
