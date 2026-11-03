package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingCategoryResponseDTO
 * @Date: 2025/9/10 14:11
 */
@Builder
@Data
public class CopywritingCategoryResponseDTO implements Serializable {

    private Long copywritingCategoryId;
    private String name;
}
