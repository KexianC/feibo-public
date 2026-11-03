package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminMembershipPlanStatusDTO {
    @NotNull
    private Boolean isActive;
}
