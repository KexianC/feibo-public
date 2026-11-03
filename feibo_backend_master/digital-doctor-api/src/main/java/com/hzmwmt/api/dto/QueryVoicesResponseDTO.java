package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryVoicesResponseDTO
 * @Date: 2025/9/24 23:18
 */
@Data
@Builder
public class QueryVoicesResponseDTO implements Serializable {
    private Long voiceId;
    private String name;
    private String styleName;
    private String image;
    private String gender;
    private Boolean isCollected;
}
