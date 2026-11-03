package com.hzmwmt.domain.user.model.entity;

import com.hzmwmt.domain.user.model.valobj.AIFeatureTemplateVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAIResourceEntity
 * @Date: 2025/9/3 14:38
 */
@Builder
@Data
public class UserAIResourceEntity {
    /**
     * 用户AI资源id
     */
    private Long userAiResourceId;
    /**
     * 权益模板
     */
    private AIFeatureTemplateVO aiFeatureTemplateVO;


}
