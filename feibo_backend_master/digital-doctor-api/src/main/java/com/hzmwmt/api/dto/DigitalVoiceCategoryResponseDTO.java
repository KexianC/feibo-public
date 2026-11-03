package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryResponseDTO
 * @Date: 2025/9/24 23:22
 */
@Data
@Builder
public class DigitalVoiceCategoryResponseDTO implements Serializable {
    private Long categoryId;
    private String name;
    private String categoryType;
}