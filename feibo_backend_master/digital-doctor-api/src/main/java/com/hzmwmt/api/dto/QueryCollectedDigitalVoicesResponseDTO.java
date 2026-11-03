package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryCollectedDigitalVoicesResponseDTO
 * @Date: 2025/9/24 23:21
 */
@Data
@Builder
public class QueryCollectedDigitalVoicesResponseDTO implements Serializable {
    private Long voiceId;
    private String name;
    private String styleName;
    private String image;
    private String gender;
}