package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingSourceResponseDTO
 * @Date: 2025/9/10 14:12
 */
@Builder
@Data
public class CopywritingSourceResponseDTO implements Serializable {
    private Long copywritingSourceId;
    private String name;
}
