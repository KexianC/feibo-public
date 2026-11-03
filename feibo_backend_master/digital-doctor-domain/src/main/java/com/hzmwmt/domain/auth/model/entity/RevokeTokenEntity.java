package com.hzmwmt.domain.auth.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RevokeTokenEntity
 * @Date: 2025/9/4 13:36
 */
@Data
@Builder
public class RevokeTokenEntity {
    private Long userId;
    private String token;
}
