package com.hzmwmt.domain.auth.model.entity;

import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ValidateAccessTokenCommand
 * @Date: 2025/8/6 10:04
 */
@Data
@Builder
public class JwtRuleCommandEntity {
    private String token;
    private String clientFingerprint;
    private JwtValidationRuleFilterFactory.TokenTypeEnumVO tokenTypeEnumVO;
    /** Signed authentication assurance extracted during token validation. */
    private boolean mfaVerified;
}
