package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAiModelVisibilityDTO {

    @NotNull
    private Boolean visible;
}
