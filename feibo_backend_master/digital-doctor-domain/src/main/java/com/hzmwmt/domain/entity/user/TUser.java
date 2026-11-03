package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user") // 根据你的实际表名调整
@Schema(
        description = "用户实体类，对应数据库表t_user，存储用户的基本信息（如昵称、头像、账号状态等）"
)
public class TUser {

    /**
     * 自增主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "自增主键ID（数据库自动生成，唯一标识一条用户记录）",
            example = "1001"
    )
    private Long id;

    /**
     * 业务ID（暂无...）
     */
    @TableField("user_id")
    @Schema(
            description = "用户业务ID（用于业务层标识用户的唯一编码，暂无具体用途可留空）",
            example = "200001",
            nullable = true
    )
    private Long userId;

    /**
     * 昵称
     */
    @TableField("nick_name")
    @Schema(
            description = "用户昵称（展示用，非登录账号）",
            example = "星辰大海"
    )
    private String nickName;

    /**
     * 头像地址
     */
    @TableField("avatar")
    @Schema(
            description = "用户头像图片URL（如https://xxx.com/avatars/user123.png）",
            example = "https://example.com/avatars/star.png",
            nullable = true
    )
    private String avatar;

    /**
     * 密码
     */
    @TableField("password")
    @JsonIgnore
    @Schema(
            description = "用户登录密码哈希（永不通过接口序列化）",
            hidden = true
    )
    private String password;

    /**
     * 手机号码
     */
    @TableField("phone_number")
    @Schema(
            description = "用户手机号码（登录账号或联系方式，11位数字）",
            example = "13800138000",
            pattern = "^1[3-9]\\d{9}$"
    )
    private String phoneNumber;

    /**
     * 性别（0:未知, 1:男, 2:女）
     */
    @TableField("gender")
    @Schema(
            description = "用户性别（0=未知，1=男，2=女）",
            example = "1",
            allowableValues = {"0", "1", "2"},
            defaultValue = "0"
    )
    private Integer gender;

    /**
     * 禁用状态（0/false:正常, 1/true:禁用）
     */
    @TableField("status")
    @Schema(
            description = "用户账号禁用状态（false=正常，true=禁用）",
            example = "false",
            allowableValues = {"true", "false"}
    )
    private Boolean status;

    /**
     * 专业方向
     */
    @TableField("specialty")
    @Schema(
            description = "用户专业方向"
    )
    private String specialty;

    /**
     * 微信ID（或其他第三方ID）
     */
    @TableField("open_id")
    @Schema(
            description = "第三方平台ID（如微信openid、支付宝uid等，用于第三方登录关联）",
            example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M",
            nullable = true
    )
    private String openId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(
            description = "用户账号创建时间（数据库插入时自动填充当前时间）",
            example = "2024-09-25T10:00:00"
    )
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField(value = "update_time")
    @Schema(
            description = "用户信息最后修改时间（数据库插入和更新时自动填充当前时间）",
            example = "2024-09-25T16:30:00"
    )
    private LocalDateTime updateTime;
}
