package com.hzmwmt.domain.user.model.entity;

import com.hzmwmt.types.enums.GenderEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthUserEntity
 * @Date: 2025/9/4 14:43
 */
@Builder
@Data
public class UserEntity {
    /**
     * 业务ID (雪花算法生成)
     */
    private Long userId;
    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 性别 (0:未知, 1:男, 2:女)
     */
    private GenderEnumVO genderEnumVO;

    /**
     * 状态 0正常 1禁用
     */
    private int status;

    /**
     * 专业方向
     */
    private String specialty;
}
