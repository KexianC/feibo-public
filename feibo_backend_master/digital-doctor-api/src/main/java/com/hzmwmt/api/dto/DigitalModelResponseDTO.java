package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalModelResponseDTO
 * @Date: 2025/9/13 14:37
 */
@Data
@Builder
public class DigitalModelResponseDTO implements Serializable {
    private Long voiceId;
    private Long imageId;
    private String name;
    private String avatar;
    private String gender;
}
