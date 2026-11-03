package com.hzmwmt.infrastructure.gateway.digital.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VideoComposeResponseDTO
 * @Date: 2025/9/24 17:56
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoComposeResponseDTO {
    private Integer code;
    private String message;
    private String data;
    private Boolean ok;
}
