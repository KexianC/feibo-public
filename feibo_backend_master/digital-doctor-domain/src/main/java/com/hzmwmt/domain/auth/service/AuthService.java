package com.hzmwmt.domain.auth.service;


import com.hzmwmt.domain.auth.adapter.port.IAuthPort;
import com.hzmwmt.domain.auth.adapter.repository.IAuthRepository;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.model.entity.RevokeTokenEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory.JwtValidationContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthService
 * @Date: 2025/7/21 11:52
 */
@Slf4j
@Service
public class AuthService implements IAuthService {
    @Resource
    private IAuthPort authPort;
    @Resource
    private IAuthRepository repository;
    @Resource
    private JwtValidationRuleFilterFactory jwtValidationRuleFilterFactory;

    @Override
    public String generateAccessToken(Long userId, String clientFingerprint, boolean mfaVerified) {
        return authPort.generateAccessToken(userId, clientFingerprint, mfaVerified);
    }

    @Override
    public String generateRefreshToken(Long userId, String clientFingerprint, boolean mfaVerified) {
        return authPort.generateRefreshToken(userId, clientFingerprint, mfaVerified);
    }

    @Override
    public RotatedTokenPairEntity rotateRefreshToken(
            String refreshToken,
            Long userId,
            String clientFingerprint) {
        return authPort.rotateRefreshToken(refreshToken, userId, clientFingerprint);
    }

    @Override
    public void revokeRefreshToken(RevokeTokenEntity revokeTokenEntity) {
        authPort.revokeRefreshToken(revokeTokenEntity);
    }

    @Override
    public String queryTokenHead() {
        return authPort.queryTokenHead();
    }

    @Override
    public void sendAuthShortMessage(String phoneNumber) {
        authPort.sendAuthShortMessage(phoneNumber);
    }

    @Override
    public AuthUserAggregate validateToken(JwtRuleCommandEntity command) throws Exception {
        return jwtValidationRuleFilterFactory.getValidationChain(command.getTokenTypeEnumVO())
                .apply(
                        command,
                        new JwtValidationContext()
                );
    }

    @Override
    public AuthUserEntity queryValidatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("用户未认证，请先登录");
        }
        return (AuthUserEntity) authentication.getPrincipal();
    }

    @Override
    public Boolean isDoctorAssistant() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_DOCTOR_ASSISTANT"));
    }

    @Override
    public Boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public Boolean hasExpertRole() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_EXPERT"));
    }

    @Override
    public Boolean hasDoctorRole() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_DOCTOR"));
    }

    @Override
    public Boolean hasOrganizationAdminRole() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ORG_ADMIN")
                        || auth.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public String queryAuthCredentials() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
    }

    @Override
    public AuthUserAggregate authUserByPassword(String phoneNumber, String password) {
        return repository.authUserByPassword(phoneNumber, password);
    }

    @Override
    public AuthUserAggregate authUserByVerificationCode(String phoneNumber, String code) {
        authPort.authUserByVerificationCode(phoneNumber, code);
        AuthUserAggregate authUserAggregate = repository.queryUserConfig(phoneNumber);
        if (authUserAggregate == null) {
            repository.registerUser(phoneNumber);
        } else {
            return authUserAggregate;
        }
        return repository.queryUserConfig(phoneNumber);
    }


}
