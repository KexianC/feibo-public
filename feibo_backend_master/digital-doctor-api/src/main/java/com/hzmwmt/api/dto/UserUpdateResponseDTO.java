package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户信息更新响应DTO
 * 用于返回用户信息更新后的基本用户信息
 *
 * @author huihui
 * @version v1.0
 * @Date: 2025/10/31
 */
@Data
@Builder
public class UserUpdateResponseDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户性别编码
     */
    private Integer gender;

    /**
     * 用户手机号
     */
    private String phoneNumber;

    /**
     * 用户专业方向
     */
    private String specialty;
}