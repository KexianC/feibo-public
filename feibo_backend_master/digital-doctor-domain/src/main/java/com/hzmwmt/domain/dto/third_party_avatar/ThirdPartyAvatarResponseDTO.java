package com.hzmwmt.domain.dto.third_party_avatar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 第三方API数字人头像响应DTO
 *
 * @author huihui
 * @version v1.0
 * @Description: 用于接收第三方API返回的数字人模型数据
 * @Date: 2025/11/18
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThirdPartyAvatarResponseDTO {
    private int code;
    private String message;
    private ThirdPartyAvatarDataDTO data;
    private boolean ok;
}

