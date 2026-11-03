package com.hzmwmt.domain.trade.model.entity;

import com.hzmwmt.domain.trade.model.valobj.AIFeatureTemplateVO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIRechargePackEntity
 * @Date: 2025/8/27 14:31
 */
@Builder
@Data
public class AIRechargePackEntity {
    /**
     * 数字包id
     */
    private Long aiRechargePackId;

    /**
     * 数字包唯一编码
     */
    private String packCode;

    /**
     * 数字包名称
     */
    private String name;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 充值的目标权益模板值对象, 数字包本身就是权益
     */
    private AIFeatureTemplateVO aiFeatureTemplateVO;

    /**
     * 充值的额度
     */
    private Integer value;

    /**
     * 数字包详细描述
     */
    private String description;
}
