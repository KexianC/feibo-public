package com.hzmwmt.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradeProductResponseDTO
 * @Date: 2025/8/27 16:34
 */
@Builder
@Data
public class TradeProductResponseDTO implements Serializable {
    /**
     * 会员套餐列表
     */
    private List<MembershipPlan> membershipPlans;

    /**
     * 充值包列表
     */
    private List<RechargePack> rechargePacks;

    /**
     * 会员套餐
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipPlan {
        /**
         * AI会员套餐id
         */
        private Long aiMembershipPlanId;

        /**
         * 套餐唯一编码
         */
        private String planCode;

        /**
         * 套餐名称
         */
        private String name;

        /**
         * 价格 (元)
         */
        private BigDecimal price;

        /**
         * 订阅有效天数 (-1表示永久)
         */
        private Integer durationDays;
        private List<AIFeature> features; // 套餐包含的权益列表
    }

    /**
     * 充值包
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RechargePack {
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
        private AIFeatureTemplate aiFeatureTemplateVO;

        /**
         * 充值的额度
         */
        private Integer value;

        /**
         * 数字包详细描述
         */
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIFeature {
        /**
         * AI权益实例id
         */
        private Long aiFeatureId;

        /**
         * 关联的权益模板值对象
         */
        private AIFeatureTemplate aiFeatureTemplate;

        /**
         * 权益的额度值
         */
        private Integer value;

    }

    /**
     * 套餐中的权益
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIFeatureTemplate {
        /**
         * 权益模板业务代码, 程序识别用
         */
        private String templateCode;

        /**
         * 权益模板名称
         */
        private String name;

        /**
         * 权益模板详细描述
         */
        private String description;
    }
}
