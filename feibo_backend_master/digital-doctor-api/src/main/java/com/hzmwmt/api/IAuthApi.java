package com.hzmwmt.api;

import com.hzmwmt.api.dto.AuthUserByPasswordRequestDTO;
import com.hzmwmt.api.dto.AuthUserByVerificationCodeRequestDTO;
import com.hzmwmt.api.dto.AuthUserResponseDTO;
import com.hzmwmt.api.dto.LogOutRequestDTO;
import com.hzmwmt.api.dto.RefreshTokenResponseDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.api.valid.Phone;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAuthApi
 * @Date: 2025/9/5 13:04
 */
public interface IAuthApi {
    /**
     * 用户密码登录
     *
     * @param authUserByPasswordRequestDTO 登录信息
     * @return 登录结果
     */
    Response<AuthUserResponseDTO> loginByPassword(@Valid AuthUserByPasswordRequestDTO authUserByPasswordRequestDTO);

    /**
     * 用户验证码登录
     *
     * @param authUserByVerificationCodeRequestDTO 登录信息
     * @return 登录结果
     */
    Response<AuthUserResponseDTO> loginByVerificationCode(@Valid AuthUserByVerificationCodeRequestDTO authUserByVerificationCodeRequestDTO);

    /**
     * Atomically consumes the presented refresh token and returns a rotated
     * access/refresh pair. Clients must use this POST endpoint.
     */
    ResponseEntity<Response<RefreshTokenResponseDTO>> rotateRefreshToken();


    /**
     * 登出
     *
     * @return 登出结果
     */
    Response<Void> logout(@Valid LogOutRequestDTO logOutRequestDTO);

    /**
     * 发送验证码
     *
     * @param phoneNumber 手机号
     * @return 发送结果
     */
    Response<Void> sendAuthShortMessage(@Phone String phoneNumber);


}
