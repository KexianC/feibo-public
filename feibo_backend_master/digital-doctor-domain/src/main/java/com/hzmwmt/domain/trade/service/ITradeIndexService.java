package com.hzmwmt.domain.trade.service;

import com.hzmwmt.domain.trade.model.aggregate.AIMembershipPlanAggregate;
import com.hzmwmt.domain.trade.model.entity.AIRechargePackEntity;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradeIndexService
 * @Date: 2025/8/27 9:28
 */
public interface ITradeIndexService {
    List<AIMembershipPlanAggregate> queryAiMemberShipPlanTradeIndex();

    List<AIRechargePackEntity> queryAiRechargePackTradeIndex();
}
