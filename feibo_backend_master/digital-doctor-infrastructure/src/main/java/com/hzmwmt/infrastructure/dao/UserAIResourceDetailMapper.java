package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.UserAIResourceDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAIResourceDetailMapper
 * @Date: 2025/9/20 19:15
 */
public interface UserAIResourceDetailMapper extends BaseMapper<UserAIResourceDetail> {

    /**
     * 锁定并查询可扣减的订阅资源详情ID
     *
     * @param userAiResourceId 用户AI资源ID
     * @return 详情记录ID，没有可扣减记录返回null
     */
    Long selectAndLockSubscriptionDetailIdByUserAiResourceIdAndValue(@Param("userAiResourceId") Long userAiResourceId, @Param("value") int value);

    /**
     * 锁定并查询可扣减的购买资源详情ID
     *
     * @param userAiResourceId 用户AI资源ID
     * @return 详情记录ID，没有可扣减记录返回null
     */
    Long selectAndLockPurchasedDetailIdByUserAiResourceIdAndValue(@Param("userAiResourceId") Long userAiResourceId, @Param("value") int value);

    /**
     * 根据详情ID扣减余额
     *
     * @param userAiResourceDetailId 用户AI资源详情ID
     * @return 影响行数
     */
    int deductBalanceByUserAiResourceDetailId(@Param("userAiResourceDetailId") Long userAiResourceDetailId, @Param("value") int value);


    /**
     * 查询用户未过期的订阅总余额
     *
     * @param userAiResourceId 用户AI资源ID
     * @return 订阅总余额
     */
    Integer SelectSubscriptionBalanceByUserAiResourceId(@Param("userAiResourceId") Long userAiResourceId);

    /**
     * 查询用户未过期的充值包总余额
     *
     * @param userAiResourceId 用户AI资源ID
     * @return 充值包总余额
     */
    Integer SelectRechargeBalanceByUserAiResourceId(@Param("userAiResourceId") Long userAiResourceId);

    /**
     * 批量插入用户AI资源详情
     *
     * @param details 用户AI资源详情列表
     * @return 影响行数
     */
    int batchInsert(@Param("details") List<UserAIResourceDetail> details);
}
