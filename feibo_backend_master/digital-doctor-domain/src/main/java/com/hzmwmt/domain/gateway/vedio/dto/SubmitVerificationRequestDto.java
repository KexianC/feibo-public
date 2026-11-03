package com.hzmwmt.domain.gateway.vedio.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SubmitVerificationRequestDto {
    @NotEmpty
    String code;
}
