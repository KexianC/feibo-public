package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AdminUserRoleMapCreateDTO {
    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long roleId;
}
