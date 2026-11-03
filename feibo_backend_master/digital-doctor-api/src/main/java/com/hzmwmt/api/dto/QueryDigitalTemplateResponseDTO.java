package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryDigitalTemplateResponseDTO
 * @Date: 2025/9/24 21:37
 */
@Data
@Builder
public class QueryDigitalTemplateResponseDTO implements Serializable {
    private Long digitalTemplateId;
    private String title;
    private String body;
    private String createTime;
}
