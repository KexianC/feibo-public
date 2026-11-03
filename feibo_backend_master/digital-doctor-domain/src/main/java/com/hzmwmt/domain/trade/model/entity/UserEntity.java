package com.hzmwmt.domain.trade.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthUserEntity
 * @Date: 2025/8/26 16:07
 */
@Data
@Builder
public class UserEntity {
    /**
     * 用户名
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
     * 手机号码
     */
    private String phoneNumber;
    /**
     * 0 男，1女，2 保密
     */
    private Integer gender;
    /**
     * 简介
     */
    private String introduction;
    /**
     * wxid
     */
    private String openId;
}
