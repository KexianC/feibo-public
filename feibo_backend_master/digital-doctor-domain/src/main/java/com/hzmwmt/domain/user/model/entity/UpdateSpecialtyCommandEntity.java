package com.hzmwmt.domain.user.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateSpecialtyCommandEntity {
    @Schema(description = "用户业务ID（userId）", example = "1976200871068037122")
    private Long userId;

    @Schema(description = "专业", example = "肿瘤科")
    private String specialty;
}