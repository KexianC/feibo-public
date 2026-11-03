package com.hzmwmt.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理员显式设置用户禁用状态")
public class AdminUserStatusUpdateDTO {

    @NotNull(message = "disabled不能为空")
    @Schema(description = "true=禁用，false=启用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean disabled;
}
