package com.hzmwmt.domain.user.model.entity;

import com.hzmwmt.types.enums.GenderEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RegisterAssistantCommandEntity
 * @Date: 2025/9/6 17:25
 */
@Builder
@Data
public class RegisterAssistantCommandEntity {
    private Long expertId;
    /**
     * 昵称
     */
    private String nickName;


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
}
