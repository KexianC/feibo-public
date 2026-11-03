package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ChatProcessAggregate
 * @Date: 2025/9/11 10:43
 */
@Builder
@Data
public class ChatProcessAggregate {
    private Long userId;
    private AIMessageEntity aiMessageEntity;
    private ResourceUserTypeEnumVO resourceUserTypeEnumVO;
    private Boolean isAllHistory;
}
