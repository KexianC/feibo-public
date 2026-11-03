package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryExpertsResponseDTO
 * @Date: 2025/9/10 15:04
 */
@Data
@Builder
public class QueryExpertsResponseDTO implements Serializable {
    /**
     * 专家的用户ID
     */
    private Long userId;

    /**
     * 专家的昵称
     */
    private String nickName;

    /**
     * 专家的头像地址
     */
    private String avatar;

    /**
     * 专家的手机号码
     */
    private String phoneNumber;

    /**
     * 专家的性别描述
     */
    private String gender;

}
