package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryAssistantsResponseDTO
 * @Date: 2025/9/10 15:04
 */
@Data
@Builder
public class QueryAssistantsResponseDTO implements Serializable {
    /**
     * 助理的用户ID
     */
    private Long userId;

    /**
     * 助理的昵称
     */
    private String nickName;

    /**
     * 助理的头像地址
     */
    private String avatar;

    /**
     * 助理的手机号码
     */
    private String phoneNumber;

    /**
     * 助理的性别描述
     */
    private String gender;
}
