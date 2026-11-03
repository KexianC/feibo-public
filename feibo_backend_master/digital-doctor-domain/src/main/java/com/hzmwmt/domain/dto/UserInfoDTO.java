package com.hzmwmt.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户信息DTO，用于返回用户基本信息（用户名和手机号）
 * 主要用于管理员查看用户相关数据时使用
 */
@Data
@Schema(description = "用户基本信息DTO，包含用户名和手机号")
public class UserInfoDTO {

    @Schema(description = "用户昵称", example = "星辰大海")
    private String username;

    @Schema(description = "用户手机号码", example = "13800138000")
    private String phone;
}