package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRoleCreateDTO {
    @NotBlank
    @Size(min = 2, max = 64)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,63}")
    private String name;

    @Size(max = 255)
    private String description;
}
