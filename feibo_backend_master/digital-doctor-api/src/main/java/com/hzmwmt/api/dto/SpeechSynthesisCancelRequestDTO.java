package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SpeechSynthesisCancelRequestDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String sessionId;
}
