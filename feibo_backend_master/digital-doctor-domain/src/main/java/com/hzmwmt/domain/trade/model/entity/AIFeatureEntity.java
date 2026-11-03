package com.hzmwmt.domain.trade.model.entity;

import com.hzmwmt.domain.trade.model.valobj.AIFeatureTemplateVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIFeatureEntity
 * @Date: 2025/8/27 9:31
 */
@Builder
@Data
public class AIFeatureEntity {
    /**
     * AI权益实例id
     */
    private Long aiFeatureId;

    /**
     * 关联的权益模板值对象
     */
    private AIFeatureTemplateVO aiFeatureTemplateVO;

    /**
     * 权益的额度值
     */
    private Integer value;

}
