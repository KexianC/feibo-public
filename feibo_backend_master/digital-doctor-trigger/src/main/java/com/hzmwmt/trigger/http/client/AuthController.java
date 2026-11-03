package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.IAuthApi;
import com.hzmwmt.api.dto.AuthUserByPasswordRequestDTO;
import com.hzmwmt.api.dto.AuthUserByVerificationCodeRequestDTO;
import com.hzmwmt.api.dto.AuthUserResponseDTO;
import com.hzmwmt.api.dto.LogOutRequestDTO;
import com.hzmwmt.api.dto.RefreshTokenResponseDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.RevokeTokenEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.dto.ChangePasswordByOldRequestDTO;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.infrastructure.gateway.security.AuthAbuseProtectionService;
import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.annotations.LoginLog;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.LoginType;
import com.hzmwmt.types.enums.RoleCode;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import com.hzmwmt.types.exception.RateLimitException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthController
 * @Date: 2025/9/5 13:06
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/auth/")
public class AuthController implements IAuthApi {
    @Resource
    private IAuthService authService;

    @Resource
    private AdminUserService adminUserService;

    @Resource
    private AuthAbuseProtectionService authAbuseProtectionService;

    @Resource
    private AdminPermissionService adminPermissionService;

    @Override
    @RequestMapping(value = "login/password", method = RequestMethod.POST)
    @Idempotent(
            message = "请勿重复点击登录"
    )
    @LoginLog(loginType = "密码登录", description = "用户密码登录")
    public Response<AuthUserResponseDTO> loginByPassword(
            @RequestBody @Valid AuthUserByPasswordRequestDTO authUserByPasswordRequestDTO) {
        String phoneNumber = authUserByPasswordRequestDTO.getPhoneNumber();
        authAbuseProtectionService.assertLoginAllowed(phoneNumber, clientAddress());

        try {
            // 1. 获取登录类型
            LoginType loginType = LoginType.getByCode(authUserByPasswordRequestDTO.getLoginType());
            log.info("密码登录开始，登录类型: {}", loginType.name());
            boolean isBackendLogin = loginType == LoginType.BACKEND;

            // 2. 密码登录
            AuthUserAggregate authUserAggregate = authService.authUserByPassword(
                    phoneNumber,
                    authUserByPasswordRequestDTO.getPassword()
            );
            AuthUserEntity authUserEntity = authUserAggregate.getAuthUserEntity();

            if (Boolean.TRUE.equals(authUserEntity.getStatus())) {
                throw new AppException(ResponseCode.E0006);
            }

            // The primary credential is valid; reset the account brute-force state.
            authAbuseProtectionService.clearSuccessfulLogin(phoneNumber);

            // 3. 后台登录权限检查
            if (isBackendLogin && !hasAdminPermission(authUserAggregate)) {
                authAbuseProtectionService.clearSuccessfulLogin(phoneNumber);
                log.warn("已验证账号尝试后台登录但无管理员权限");
                return Response.<AuthUserResponseDTO>builder()
                        .code(ResponseCode.FORBIDDEN.getCode())
                        .info("权限不足，非管理员用户无法登录后台系统")
                        .build();
            }

            List<String> permissions = adminPermissionService.getPermissionCodesByUserId(
                    authUserEntity.getUserId());

            // 4. 生成 Token
            String accessToken = authService.generateAccessToken(
                    authUserEntity.getUserId(),
                    authUserByPasswordRequestDTO.getClientFingerprint(),
                    false
            );
            String refreshToken = authService.generateRefreshToken(
                    authUserEntity.getUserId(),
                    authUserByPasswordRequestDTO.getClientFingerprint(),
                    false
            );
            String tokenHead = authService.queryTokenHead();

            // 5. 构建响应
            return buildLoginSuccessResponse(
                    authUserEntity,
                    authUserAggregate,
                    accessToken,
                    refreshToken,
                    tokenHead,
                    loginType,
                    permissions
            );

        } catch (AuthorizationDataUnavailableException exception) {
            throw exception;
        } catch (AppException e) {
            if (e instanceof RateLimitException) {
                throw e;
            }
            if (isCredentialFailure(e)) {
                log.warn("密码登录凭据校验失败");
                return invalidCredentialResponse("手机号或密码错误");
            }
            log.error("密码登录业务异常，code={}", e.getCode());
            return Response.<AuthUserResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("密码登录服务异常，异常类型: {}", e.getClass().getSimpleName());
            return Response.<AuthUserResponseDTO>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info("登录失败，请稍后重试")
                    .build();
        }
    }


    @Override
    @RequestMapping(value = "login/verification-code", method = RequestMethod.POST)
    @Idempotent(message = "请勿重复点击登录")
    @LoginLog(loginType = "验证码登录", description = "用户验证码登录")
    public Response<AuthUserResponseDTO> loginByVerificationCode(
            @RequestBody @Valid AuthUserByVerificationCodeRequestDTO requestDTO) {
        String phoneNumber = requestDTO.getPhoneNumber();
        authAbuseProtectionService.assertLoginAllowed(phoneNumber, clientAddress());

        try {
            // 1. 获取登录类型
            LoginType loginType = LoginType.getByCode(requestDTO.getLoginType());
            log.info("验证码登录开始，登录类型: {}", loginType.name());
            boolean isBackendLogin = loginType == LoginType.BACKEND;

            // 2. 验证码登录
            AuthUserAggregate authUserAggregate = authService.authUserByVerificationCode(
                    requestDTO.getPhoneNumber(),
                    requestDTO.getVerificationCode()
            );
            AuthUserEntity authUserEntity = authUserAggregate.getAuthUserEntity();

            if (Boolean.TRUE.equals(authUserEntity.getStatus())) {
                throw new AppException(ResponseCode.E0006);
            }

            // 3. 后台登录权限检查
            if (isBackendLogin && !hasAdminPermission(authUserAggregate)) {
                authAbuseProtectionService.clearSuccessfulLogin(phoneNumber);
                log.warn("已验证账号尝试后台登录但无管理员权限");
                return Response.<AuthUserResponseDTO>builder()
                        .code(ResponseCode.FORBIDDEN.getCode())
                        .info("权限不足，非管理员用户无法登录后台系统")
                        .build();
            }

            List<String> permissions = adminPermissionService.getPermissionCodesByUserId(
                    authUserEntity.getUserId());

            // 4. 生成 Token
            String accessToken = authService.generateAccessToken(
                    authUserEntity.getUserId(), requestDTO.getClientFingerprint(), false);
            String refreshToken = authService.generateRefreshToken(
                    authUserEntity.getUserId(), requestDTO.getClientFingerprint(), false);
            String tokenHead = authService.queryTokenHead();
            authAbuseProtectionService.clearSuccessfulLogin(phoneNumber);

            // 5. 构建响应
            return buildLoginSuccessResponse(authUserEntity, authUserAggregate,
                    accessToken, refreshToken, tokenHead, loginType, permissions);

        } catch (AuthorizationDataUnavailableException exception) {
            throw exception;
        } catch (AppException e) {
            if (e instanceof RateLimitException) {
                throw e;
            }
            if (isCredentialFailure(e) || ResponseCode.ILLEGAL_PARAMETER.getCode().equals(e.getCode())) {
                log.warn("验证码登录凭据校验失败");
                return invalidCredentialResponse("手机号或验证码错误");
            }
            log.error("验证码登录业务异常，code={}", e.getCode());
            return Response.<AuthUserResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("验证码登录服务异常，异常类型: {}", e.getClass().getSimpleName());
            return Response.<AuthUserResponseDTO>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info("登录失败，请稍后重试")
                    .build();
        }
    }


    /**
     * 检查用户是否有管理员权限
     */
    private boolean hasAdminPermission(AuthUserAggregate authUserAggregate) {
        return authUserAggregate.getRoleEntityList().stream()
                .anyMatch(role -> isAdminRole(role.getName()));
    }

    /**
     * 判断角色是否为管理员角色
     */
    private boolean isAdminRole(String roleName) {
        return RoleCode.grantsAdminConsole(roleName);
    }

    private boolean isCredentialFailure(AppException exception) {
        return ResponseCode.E0003.getCode().equals(exception.getCode())
                || ResponseCode.E0005.getCode().equals(exception.getCode())
                || ResponseCode.E0006.getCode().equals(exception.getCode());
    }

    private Response<AuthUserResponseDTO> invalidCredentialResponse(String message) {
        return Response.<AuthUserResponseDTO>builder()
                .code(ResponseCode.E0003.getCode())
                .info(message)
                .build();
    }

    private String clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    /**
     * 构建登录成功响应
     *
     * @param authUserEntity    用户实体信息
     * @param authUserAggregate 用户聚合根（包含角色信息）
     * @param accessToken       访问令牌
     * @param refreshToken      刷新令牌
     * @param tokenHead         token前缀
     * @param loginType         登录类型枚举
     * @return 登录成功的响应对象
     */
    private Response<AuthUserResponseDTO> buildLoginSuccessResponse(
            AuthUserEntity authUserEntity,
            AuthUserAggregate authUserAggregate,
            String accessToken,
            String refreshToken,
            String tokenHead,
            LoginType loginType,
            List<String> permissions) {

        boolean isBackendLogin = loginType == LoginType.BACKEND;

        return Response.<AuthUserResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(AuthUserResponseDTO.builder()
                        .userId(authUserEntity.getUserId())
                        .nickName(authUserEntity.getNickName())
                        .avatar(authUserEntity.getAvatar())
                        .phoneNumber(authUserEntity.getPhoneNumber())
                        .gender(authUserEntity.getGenderEnumVO().getDescription())
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenHead(tokenHead)
                        .loginType(loginType.getCode())
                        .loginTypeDesc(loginType.getDescription())
                        .hasAdminPermission(isBackendLogin)
                        .roles(authUserAggregate.getRoleEntityList().stream().map(
                                        roleEntity -> AuthUserResponseDTO.RoleDTO.builder()
                                                .roleId(roleEntity.getRoleId())
                                                .name(roleEntity.getName())
                                                .description(roleEntity.getDescription())
                                                .isAdmin(isAdminRole(roleEntity.getName()))
                                                .build()
                                ).toList()
                        )
                        .permissions(permissions)
                        .build())
                .build();
    }


    @Override
    @PostMapping("refresh/rotate")
    public ResponseEntity<Response<RefreshTokenResponseDTO>> rotateRefreshToken() {
        HttpServletRequest request = currentRequest();
        String refreshToken = extractBearerToken(request);
        String clientFingerprint = authService.queryAuthCredentials();
        AuthUserEntity authUser = authService.queryValidatedUser();
        RotatedTokenPairEntity rotated = authService.rotateRefreshToken(
                refreshToken,
                authUser.getUserId(),
                clientFingerprint);

        Response<RefreshTokenResponseDTO> body = Response.<RefreshTokenResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(RefreshTokenResponseDTO.builder()
                        .accessToken(rotated.getAccessToken())
                        .refreshToken(rotated.getRefreshToken())
                        .tokenHead(authService.queryTokenHead())
                        .build())
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @Override
    @RequestMapping(value = "logout", method = RequestMethod.POST)
    @Idempotent(
            message = "请勿重复注销"
    )
    @OperationLog(operationType = "退出", description = "用户登出", recordParams = true)
    public Response<Void> logout(@RequestBody @Valid LogOutRequestDTO logOutRequestDTO) {
        AuthUserEntity userEntity = authService.queryValidatedUser();
        log.info("用户 {} 正在请求注销一个刷新令牌...", userEntity.getUserId());

        RevokeTokenEntity revokeTokenEntity = RevokeTokenEntity.builder()
                .userId(userEntity.getUserId())
                .token(logOutRequestDTO.getRefreshToken())
                .build();

        authService.revokeRefreshToken(revokeTokenEntity);

        log.info("用户 {} 的刷新令牌已成功注销。", userEntity.getUserId());

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "/login/send", method = RequestMethod.POST)
    @Idempotent(
            expireTime = 1,
            message = "请勿重复发送验证码"
    )
    public Response<Void> sendAuthShortMessage(@RequestParam String phoneNumber) {
        authAbuseProtectionService.assertSmsSendAllowed(phoneNumber, clientAddress());
        authService.sendAuthShortMessage(phoneNumber);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    /**
     * 通过旧密码修改新密码
     */
    @PostMapping("/change-password-by-old")
    @OperationLog(operationType = "更新", description = "修改密码", recordParams = true)
    public Response<Void> changePasswordByOld(@RequestBody @Valid ChangePasswordByOldRequestDTO dto) {
        // 从登录态获取当前用户ID
        AuthUserEntity authUser = authService.queryValidatedUser();
        adminUserService.changePasswordByOld(authUser.getUserId(), dto);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new AppException(ResponseCode.UNAUTHORIZED);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }
        return token;
    }


}
