package com.hzmwmt.domain.digital.model.entity;

import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VideoCompositionEntity
 * @Date: 2025/9/25 14:41
 */
@Builder
@Data
public class VideoCompositionEntity {
    private Long userId;
    private String requestId;
    private Integer videoTime;
    private String body;
    private ResourceUserTypeEnumVO resourceUserTypeEnumVO;
}
