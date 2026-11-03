package com.hzmwmt.domain.auth.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A newly issued access/refresh token pair from one atomic refresh rotation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RotatedTokenPairEntity {
    private String accessToken;
    private String refreshToken;
}
