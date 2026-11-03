package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryAIQuotaResponseDTO
 * @Date: 2025/9/17 16:09
 */
@Data
@Builder
public class QueryAIQuotaResponseDTO implements Serializable {
    private Boolean isReceived;
    private String expireTime;
}
