package com.hzmwmt.api.dto;

import com.hzmwmt.api.valid.JsonString;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVideoCompositionRequestDTO
 * @Date: 2025/9/24 20:06
 */
@Data
public class DigitalVideoCompositionRequestDTO {
    @JsonString
    private String body;
    @NotNull
    private Integer videoTime;
}
