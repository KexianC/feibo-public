package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalAudioResponseDTO
 * @Date: 2025/9/13 14:37
 */
@Data
@Builder
public class DigitalAudioResponseDTO implements Serializable {
    private String name;
    private String url;
    private Integer duration;
}