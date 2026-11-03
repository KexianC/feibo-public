package com.hzmwmt.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIQuotaEntity
 * @Date: 2025/9/17 15:25
 */
@Builder
@Data
public class AIQuotaEntity {
    private Boolean isReceived;
    private String expireTime;
}
