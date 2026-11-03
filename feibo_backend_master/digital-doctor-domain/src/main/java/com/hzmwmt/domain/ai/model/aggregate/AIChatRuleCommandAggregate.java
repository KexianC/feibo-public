package com.hzmwmt.domain.ai.model.aggregate;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIChatRuleCommandAggregate
 * @Date: 2025/9/21 14:52
 */
@Builder
@Data
public class AIChatRuleCommandAggregate {
    //用户Id
    private Long userId;
    //模型实体
    private AIModelConfigEntity aiModelConfigLeafEntity;
    //消息实体
    private AIMessageEntity aiMessageEntity;
    //资源明细Id用于回滚
    private Long aiResourceDetailId;
    //用户类型
    private ResourceUserTypeEnumVO resourceUserTypeEnumVO;

}
