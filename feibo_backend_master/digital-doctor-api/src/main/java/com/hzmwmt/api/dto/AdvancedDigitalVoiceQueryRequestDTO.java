package com.hzmwmt.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AdvancedDigitalVoiceQueryRequestDTO
 * @Date: 2025/9/24 23:22
 */
@Data
public class AdvancedDigitalVoiceQueryRequestDTO {

    private Long lastCursorId;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 50, message = "每页数量不能超过50")
    private Integer size;
    private Boolean voiceGender;
    private String name;
    private List<Long> digitalVoiceCategoryIdList;
}
