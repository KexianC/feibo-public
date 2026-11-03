package com.hzmwmt.domain.auth.service;

import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.model.entity.RevokeTokenEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAuthService
 * @Date: 2025/9/4 9:31
 */
public interface IAuthService {

    String generateAccessToken(Long userId, String clientFingerprint, boolean mfaVerified);

    String generateRefreshToken(Long userId, String clientFingerprint, boolean mfaVerified);

    RotatedTokenPairEntity rotateRefreshToken(String refreshToken, Long userId, String clientFingerprint);

    void revokeRefreshToken(RevokeTokenEntity revokeTokenEntity);

    AuthUserAggregate validateToken(JwtRuleCommandEntity command) throws Exception;

    AuthUserEntity queryValidatedUser();

    Boolean isDoctorAssistant();

    Boolean isAdmin();

    Boolean hasExpertRole();

    Boolean hasDoctorRole();

    Boolean hasOrganizationAdminRole();

    String queryAuthCredentials();

    String queryTokenHead();

    void sendAuthShortMessage(String phoneNumber);

    AuthUserAggregate authUserByPassword(String phoneNumber, String password);

    AuthUserAggregate authUserByVerificationCode(String phoneNumber, String code);

}
