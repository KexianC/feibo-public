package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionUpdateDTO {

    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "权限版本格式无效")
    private String version;

    @NotNull
    @Size(max = 256)
    private List<@NotNull Long> permissionIds;

    /** Must be true when intentionally clearing every permission. */
    private Boolean confirmEmpty = false;
}
