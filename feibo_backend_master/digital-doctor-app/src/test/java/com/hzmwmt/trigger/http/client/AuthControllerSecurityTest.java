package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.dto.AuthUserByPasswordRequestDTO;
import com.hzmwmt.api.dto.AuthUserByVerificationCodeRequestDTO;
import com.hzmwmt.api.dto.AuthUserResponseDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.RoleEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.infrastructure.gateway.security.AuthAbuseProtectionService;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import com.hzmwmt.types.exception.RateLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerSecurityTest {

    @Mock
    private IAuthService authService;
    @Mock
    private AuthAbuseProtectionService protectionService;
    @Mock
    private AdminPermissionService permissionService;

    private AuthController controller;
    private AuthUserByPasswordRequestDTO requestDto;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "authAbuseProtectionService", protectionService);
        ReflectionTestUtils.setField(controller, "adminPermissionService", permissionService);

        requestDto = new AuthUserByPasswordRequestDTO();
        ReflectionTestUtils.setField(requestDto, "phoneNumber", "13175144211");
        ReflectionTestUtils.setField(requestDto, "password", "not-logged");
        ReflectionTestUtils.setField(requestDto, "clientFingerprint", "test-device");
        ReflectionTestUtils.setField(requestDto, "loginType", 1);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unknownAccountAndWrongPasswordHaveTheSamePublicResponse() {
        when(authService.authUserByPassword(anyString(), anyString()))
                .thenThrow(new AppException(ResponseCode.E0005))
                .thenThrow(new AppException(ResponseCode.E0003));

        Response<AuthUserResponseDTO> unknown = controller.loginByPassword(requestDto);
        Response<AuthUserResponseDTO> wrongPassword = controller.loginByPassword(requestDto);

        assertEquals(ResponseCode.E0003.getCode(), unknown.getCode());
        assertEquals(unknown.getCode(), wrongPassword.getCode());
        assertEquals(unknown.getInfo(), wrongPassword.getInfo());
    }

    @Test
    void distributedLimitStopsBeforeCredentialVerification() {
        doThrow(new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION))
                .when(protectionService).assertLoginAllowed("13175144211", "203.0.113.9");

        assertThrows(AppException.class, () -> controller.loginByPassword(requestDto));

        verify(authService, never()).authUserByPassword(anyString(), anyString());
    }

    @Test
    void disabledAccountNeverReceivesTokens() {
        when(authService.authUserByPassword(anyString(), anyString()))
                .thenReturn(aggregate(true));

        Response<AuthUserResponseDTO> response = controller.loginByPassword(requestDto);

        assertEquals(ResponseCode.E0003.getCode(), response.getCode());
        verify(authService, never()).generateAccessToken(anyLong(), anyString(), anyBoolean());
        verify(authService, never()).generateRefreshToken(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void validFrontendLoginStillIssuesTokensAndClearsOnlyAccountBudget() {
        when(authService.authUserByPassword(anyString(), anyString())).thenReturn(aggregate(false));
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenReturn(List.of("admin.console", "user.read"));

        Response<AuthUserResponseDTO> response = controller.loginByPassword(requestDto);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertNotNull(response.getData());
        assertEquals("access", response.getData().getAccessToken());
        assertEquals(List.of("admin.console", "user.read"), response.getData().getPermissions());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
        verify(protectionService).clearSuccessfulLogin("13175144211");
    }

    @Test
    void unavailableCapabilityStoreFailsClosedBeforeTokensAreIssued() {
        when(authService.authUserByPassword(anyString(), anyString())).thenReturn(aggregate(false));
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenThrow(new AuthorizationDataUnavailableException(
                        new IllegalStateException("permission table unavailable")));

        assertThrows(
                AuthorizationDataUnavailableException.class,
                () -> controller.loginByPassword(requestDto));
        verify(authService, never()).generateAccessToken(
                anyLong(), anyString(), anyBoolean());
        verify(authService, never()).generateRefreshToken(
                anyLong(), anyString(), anyBoolean());
    }

    @Test
    void superAdminBackendPasswordLoginDoesNotRequireMfa() {
        assertSuperAdminPasswordLoginWithoutMfa(2);
    }

    @Test
    void superAdminFrontendPasswordLoginDoesNotRequireMfa() {
        assertSuperAdminPasswordLoginWithoutMfa(1);
    }

    @Test
    void superAdminUnknownLoginTypeStillUsesPasswordOnly() {
        assertSuperAdminPasswordLoginWithoutMfa(999);
    }

    @Test
    void superAdminNullLoginTypeStillUsesPasswordOnly() {
        assertSuperAdminPasswordLoginWithoutMfa(null);
    }

    @Test
    void legacySuperAdminRoleAliasDoesNotRequireMfa() {
        assertSuperAdminPasswordLoginWithoutMfa(1, "SUPERADMIN");
    }

    @Test
    void legacySecondFactorFieldIsIgnoredForSuperAdminPasswordLogin() {
        ReflectionTestUtils.setField(requestDto, "verificationCode", "123456");
        assertSuperAdminPasswordLoginWithoutMfa(2);
    }

    @Test
    void superAdminBackendSmsLoginIssuesUnassuredTokens() {
        assertSuperAdminSmsLoginSucceeds(2, "SUPER_ADMIN");
    }

    @Test
    void superAdminFrontendSmsLoginIssuesUnassuredTokens() {
        assertSuperAdminSmsLoginSucceeds(1, "SUPER_ADMIN");
    }

    @Test
    void superAdminSmsWithUnknownLoginTypeUsesFrontendDefault() {
        assertSuperAdminSmsLoginSucceeds(999, "SUPER_ADMIN");
    }

    @Test
    void superAdminSmsWithNullLoginTypeUsesServiceFrontendDefault() {
        // Direct invocation checks the existing enum fallback; HTTP @Valid still rejects explicit null.
        assertSuperAdminSmsLoginSucceeds(null, "SUPER_ADMIN");
    }

    @Test
    void legacySuperAdminBackendSmsLoginPreservesRoleAndPermissions() {
        assertSuperAdminSmsLoginSucceeds(2, "SUPERADMIN");
    }

    @Test
    void superAdminSmsLoginPreservesAllAssignedRolesAndPermissions() {
        AuthUserAggregate user = aggregate(false, "SUPER_ADMIN");
        user.setRoleEntityList(List.of(
                RoleEntity.builder().roleId(1L).name("SUPER_ADMIN").description("超级管理员").build(),
                RoleEntity.builder().roleId(2L).name("DOCTOR").description("医生").build()));
        when(authService.authUserByVerificationCode("13175144211", "123456")).thenReturn(user);
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenReturn(List.of("admin.console", "system.manage", "user.read"));
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");

        Response<AuthUserResponseDTO> response = controller.loginByVerificationCode(smsRequest(2));

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertNotNull(response.getData());
        assertEquals(List.of("SUPER_ADMIN", "DOCTOR"), response.getData().getRoles().stream()
                .map(AuthUserResponseDTO.RoleDTO::getName).toList());
        assertEquals(List.of(1L, 2L), response.getData().getRoles().stream()
                .map(AuthUserResponseDTO.RoleDTO::getRoleId).toList());
        assertEquals(List.of("超级管理员", "医生"), response.getData().getRoles().stream()
                .map(AuthUserResponseDTO.RoleDTO::getDescription).toList());
        assertEquals(List.of(true, false), response.getData().getRoles().stream()
                .map(AuthUserResponseDTO.RoleDTO::getIsAdmin).toList());
        assertEquals(List.of("admin.console", "system.manage", "user.read"), response.getData().getPermissions());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
    }

    @Test
    void disabledSuperAdminSmsLoginNeverIssuesTokens() {
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenReturn(aggregate(true, "SUPER_ADMIN"));

        Response<AuthUserResponseDTO> response = controller.loginByVerificationCode(smsRequest(2));

        assertEquals(ResponseCode.E0003.getCode(), response.getCode());
        assertNull(response.getData());
        verify(permissionService, never()).getPermissionCodesByUserId(anyLong());
        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void wrongAndExpiredSmsCodesHaveTheSamePublicResponseAndIssueNoTokens() {
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenThrow(new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码错误"))
                .thenThrow(new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码已过期请重新发送"));

        Response<AuthUserResponseDTO> wrong = controller.loginByVerificationCode(smsRequest(2));
        Response<AuthUserResponseDTO> expired = controller.loginByVerificationCode(smsRequest(2));

        assertEquals(ResponseCode.E0003.getCode(), wrong.getCode());
        assertEquals(wrong.getCode(), expired.getCode());
        assertEquals("手机号或验证码错误", wrong.getInfo());
        assertEquals(wrong.getInfo(), expired.getInfo());
        assertNull(wrong.getData());
        assertNull(expired.getData());
        verify(permissionService, never()).getPermissionCodesByUserId(anyLong());
        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void ordinaryUserCannotUseValidSmsCodeToEnterBackend() {
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenReturn(aggregate(false, "DOCTOR"));

        Response<AuthUserResponseDTO> response = controller.loginByVerificationCode(smsRequest(2));

        assertEquals(ResponseCode.FORBIDDEN.getCode(), response.getCode());
        assertNull(response.getData());
        verify(permissionService, never()).getPermissionCodesByUserId(anyLong());
        verify(protectionService).clearSuccessfulLogin("13175144211");
        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void superAdminSmsLoginFailsClosedWhenPermissionsAreUnavailable() {
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenReturn(aggregate(false, "SUPER_ADMIN"));
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenThrow(new AuthorizationDataUnavailableException(
                        new IllegalStateException("permission table unavailable")));

        assertThrows(AuthorizationDataUnavailableException.class,
                () -> controller.loginByVerificationCode(smsRequest(2)));

        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void smsLoginRateLimitStopsBeforeOtpVerification() {
        doThrow(new RateLimitException("登录尝试次数过多，请稍后重试"))
                .when(protectionService).assertLoginAllowed("13175144211", "203.0.113.9");

        assertThrows(RateLimitException.class,
                () -> controller.loginByVerificationCode(smsRequest(2)));

        verify(authService, never()).authUserByVerificationCode(anyString(), anyString());
        verify(permissionService, never()).getPermissionCodesByUserId(anyLong());
        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void ordinaryAdminBackendPasswordLoginRemainsSingleFactor() {
        ReflectionTestUtils.setField(requestDto, "loginType", 2);
        when(authService.authUserByPassword(anyString(), anyString()))
                .thenReturn(aggregate(false, "ADMIN"));
        when(permissionService.getPermissionCodesByUserId(1001L)).thenReturn(List.of("admin.console"));
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");

        Response<AuthUserResponseDTO> response = controller.loginByPassword(requestDto);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        verify(authService, never()).authUserByVerificationCode(anyString(), anyString());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
    }

    @Test
    void ordinaryUserSmsLoginStillIssuesUnassuredTokens() {
        AuthUserByVerificationCodeRequestDTO smsRequest = smsRequest(1);
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenReturn(aggregate(false, "DOCTOR"));
        when(permissionService.getPermissionCodesByUserId(1001L)).thenReturn(List.of());
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");

        Response<AuthUserResponseDTO> response = controller.loginByVerificationCode(smsRequest);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
    }

    @Test
    void smsLoginOtpRateLimitEscapesControllerForHttp429Handling() {
        AuthUserByVerificationCodeRequestDTO smsRequest = new AuthUserByVerificationCodeRequestDTO();
        ReflectionTestUtils.setField(smsRequest, "phoneNumber", "13175144211");
        ReflectionTestUtils.setField(smsRequest, "verificationCode", "123456");
        ReflectionTestUtils.setField(smsRequest, "clientFingerprint", "test-device");
        ReflectionTestUtils.setField(smsRequest, "loginType", 1);
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenThrow(new RateLimitException("验证码尝试次数过多，请重新获取"));

        assertThrows(RateLimitException.class, () -> controller.loginByVerificationCode(smsRequest));

        assertSmsLoginIssuedNoTokens();
    }

    @Test
    void legacyGetRefreshIsUnmappedAndCannotMintAnAccessToken() throws Exception {
        MockMvcBuilders.standaloneSetup(controller)
                .build()
                .perform(get("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer old-refresh"))
                .andExpect(status().isNotFound());

        verify(authService, never()).generateAccessToken(anyLong(), anyString(), anyBoolean());
        verify(authService, never()).rotateRefreshToken(anyString(), anyLong(), anyString());
    }

    @Test
    void legitimateRotationReturnsBothTokensAndDisablesCaching() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/v1/auth/refresh/rotate");
        servletRequest.addHeader("Authorization", "Bearer old-refresh");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        when(authService.queryAuthCredentials()).thenReturn("test-device");
        when(authService.queryValidatedUser()).thenReturn(aggregate(false).getAuthUserEntity());
        when(authService.rotateRefreshToken("old-refresh", 1001L, "test-device"))
                .thenReturn(RotatedTokenPairEntity.builder()
                        .accessToken("new-access")
                        .refreshToken("new-refresh")
                        .build());
        when(authService.queryTokenHead()).thenReturn("Bearer");

        ResponseEntity<com.hzmwmt.api.response.Response<com.hzmwmt.api.dto.RefreshTokenResponseDTO>> response =
                controller.rotateRefreshToken();

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
        assertEquals("new-access", response.getBody().getData().getAccessToken());
        assertEquals("new-refresh", response.getBody().getData().getRefreshToken());
    }

    private AuthUserAggregate aggregate(boolean disabled) {
        return aggregate(disabled, "DOCTOR");
    }

    private AuthUserAggregate aggregate(boolean disabled, String roleName) {
        return AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder()
                        .userId(1001L)
                        .nickName("tester")
                        .phoneNumber("13175144211")
                        .avatar("")
                        .genderEnumVO(GenderEnumVO.UNKNOWN)
                        .status(disabled)
                        .build())
                .roleEntityList(List.of(RoleEntity.builder()
                        .roleId(1L)
                        .name(roleName)
                        .description(roleName)
                        .build()))
                .build();
    }

    private void assertSuperAdminPasswordLoginWithoutMfa(Integer loginType) {
        assertSuperAdminPasswordLoginWithoutMfa(loginType, "SUPER_ADMIN");
    }

    private void assertSuperAdminPasswordLoginWithoutMfa(Integer loginType, String roleName) {
        ReflectionTestUtils.setField(requestDto, "loginType", loginType);
        when(authService.authUserByPassword(anyString(), anyString()))
                .thenReturn(aggregate(false, roleName));
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenReturn(List.of("admin.console"));
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");

        Response<AuthUserResponseDTO> response = controller.loginByPassword(requestDto);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertNotNull(response.getData());
        assertEquals("access", response.getData().getAccessToken());
        verify(authService, never()).authUserByVerificationCode(anyString(), anyString());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
    }

    private void assertSuperAdminSmsLoginSucceeds(Integer loginType, String roleName) {
        AuthUserByVerificationCodeRequestDTO smsRequest = smsRequest(loginType);
        when(authService.authUserByVerificationCode("13175144211", "123456"))
                .thenReturn(aggregate(false, roleName));
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenReturn(List.of("admin.console", "system.manage"));
        when(authService.generateAccessToken(1001L, "test-device", false)).thenReturn("access");
        when(authService.generateRefreshToken(1001L, "test-device", false)).thenReturn("refresh");
        when(authService.queryTokenHead()).thenReturn("Bearer");

        Response<AuthUserResponseDTO> response = controller.loginByVerificationCode(smsRequest);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertNotNull(response.getData());
        assertEquals(1001L, response.getData().getUserId());
        assertEquals("access", response.getData().getAccessToken());
        assertEquals("refresh", response.getData().getRefreshToken());
        assertEquals("Bearer", response.getData().getTokenHead());
        boolean backend = Integer.valueOf(2).equals(loginType);
        assertEquals(backend ? 2 : 1, response.getData().getLoginType());
        assertEquals(backend, response.getData().getHasAdminPermission());
        assertEquals(1, response.getData().getRoles().size());
        assertEquals(roleName, response.getData().getRoles().get(0).getName());
        assertEquals(true, response.getData().getRoles().get(0).getIsAdmin());
        assertEquals(List.of("admin.console", "system.manage"), response.getData().getPermissions());
        verify(authService).authUserByVerificationCode("13175144211", "123456");
        verify(authService, never()).authUserByPassword(anyString(), anyString());
        verify(authService).generateAccessToken(1001L, "test-device", false);
        verify(authService).generateRefreshToken(1001L, "test-device", false);
        verify(protectionService).assertLoginAllowed("13175144211", "203.0.113.9");
        verify(protectionService).clearSuccessfulLogin("13175144211");
    }

    private void assertSmsLoginIssuedNoTokens() {
        verify(authService, never()).generateAccessToken(anyLong(), anyString(), anyBoolean());
        verify(authService, never()).generateRefreshToken(anyLong(), anyString(), anyBoolean());
    }

    private AuthUserByVerificationCodeRequestDTO smsRequest(Integer loginType) {
        AuthUserByVerificationCodeRequestDTO smsRequest = new AuthUserByVerificationCodeRequestDTO();
        ReflectionTestUtils.setField(smsRequest, "phoneNumber", "13175144211");
        ReflectionTestUtils.setField(smsRequest, "verificationCode", "123456");
        ReflectionTestUtils.setField(smsRequest, "clientFingerprint", "test-device");
        ReflectionTestUtils.setField(smsRequest, "loginType", loginType);
        return smsRequest;
    }
}
