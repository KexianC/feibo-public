package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Request payload for a single speech-synthesis segment. */
@Data
public class SpeechSynthesisRequestDTO {

    public static final int MAX_TEXT_LENGTH = 600;

    @NotBlank(message = "待朗读文本不能为空")
    @Size(max = MAX_TEXT_LENGTH, message = "单次待朗读文本不能超过600个字符")
    private String text;

    /** Optional together, so already-open older frontends remain compatible. */
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String sessionId;

    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String requestId;
}
