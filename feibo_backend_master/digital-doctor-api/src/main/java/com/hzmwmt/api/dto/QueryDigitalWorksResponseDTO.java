package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: 数字人作品查询响应DTO
 * @ClassName QueryDigitalWorksResponseDTO
 * @Date: 2025/9/25 9:56
 */
@Data
@Builder
public class QueryDigitalWorksResponseDTO implements Serializable {
    private Long digitalWorkId;
    private String title;
    private String body;
    private String externalId;
    private Integer status;
    private String failureReason;
    private String createTime;

}
