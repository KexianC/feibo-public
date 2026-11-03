package com.hzmwmt.domain.auth.adapter.port;

import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.model.entity.RevokeTokenEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAuthPort
 * @Date: 2025/9/4 10:25
 */
public interface IAuthPort {
    Long signatureValidation(JwtRuleCommandEntity command);

    void blacklistValidation(String token);

    String generateAccessToken(Long userId, String clientFingerprint, boolean mfaVerified);

    String generateRefreshToken(Long userId, String clientFingerprint, boolean mfaVerified);

    RotatedTokenPairEntity rotateRefreshToken(String refreshToken, Long userId, String clientFingerprint);

    void revokeRefreshToken(RevokeTokenEntity revokeTokenEntity);

    String queryTokenHead();

    void sendAuthShortMessage(String phoneNumber);

    void authUserByVerificationCode(String phoneNumber, String code);


}
