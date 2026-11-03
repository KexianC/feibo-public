package com.hzmwmt.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserResourceResponseDTO
 * @Date: 2025/9/3 15:44
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResourceResponseDTO implements Serializable {

    /**
     * 用户AI资源权益列表
     */
    private List<UserResource> userResources;

    /**
     * 用户订阅的会员计划列表
     */
    private List<UserSubscription> userSubscriptions;


    /**
     * 用户资源权益
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResource {
        /**
         * 用户AI资源id
         */
        private Long userAiResourceId;

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

        /**
         * 订阅权益余额 (来自会员套餐)
         */
        private Integer subscriptionBalance;

        /**
         * 购买资源包余额
         */
        private Integer purchasedBalance;
    }

    /**
     * 用户订阅的会员计划
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSubscription {
        /**
         * 用户AI订阅id
         */
        private Long userAiSubscriptionId;
        /**
         * 套餐名称
         */
        private String name;
        /**
         * 套餐唯一编码
         */
        private String planCode;
        /**
         * 订阅开始时间
         */
        private Date startTime;

        /**
         * 订阅结束时间
         */
        private Date endTime;

    }


}