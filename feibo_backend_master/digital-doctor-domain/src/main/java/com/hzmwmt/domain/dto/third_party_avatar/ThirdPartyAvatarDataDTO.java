package com.hzmwmt.domain.dto.third_party_avatar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 第三方API响应数据DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThirdPartyAvatarDataDTO {
    private List<ThirdPartyAvatarRecordDTO> records;
    private Integer total;
}
