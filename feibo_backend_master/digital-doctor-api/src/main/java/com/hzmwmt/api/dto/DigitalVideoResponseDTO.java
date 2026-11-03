package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: 数字人视频响应DTO
 * @ClassName DigitalVideoResponseDTO
 * @Date: 2025/9/13 14:38
 */
@Data
@Builder
public class DigitalVideoResponseDTO implements Serializable {
    private String name;
    private String url;
    private Integer duration;
    private Integer status;
}
