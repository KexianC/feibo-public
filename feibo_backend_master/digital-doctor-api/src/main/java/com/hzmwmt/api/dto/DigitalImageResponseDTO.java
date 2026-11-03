package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalImageResponseDTO
 * @Date: 2025/9/13 14:38
 */
@Builder
@Data
public class DigitalImageResponseDTO implements Serializable {
    private String name;
    private String url;
}
