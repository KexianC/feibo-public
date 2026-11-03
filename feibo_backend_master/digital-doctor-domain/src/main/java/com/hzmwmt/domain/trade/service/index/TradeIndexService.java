package com.hzmwmt.domain.trade.service.index;

import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.model.aggregate.AIMembershipPlanAggregate;
import com.hzmwmt.domain.trade.model.entity.AIRechargePackEntity;
import com.hzmwmt.domain.trade.service.ITradeIndexService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradeIndexService
 * @Date: 2025/8/27 9:29
 */
@Service
public class TradeIndexService implements ITradeIndexService {
    @Resource
    private ITradeRepository tradeRepository;

    @Override
    public List<AIMembershipPlanAggregate> queryAiMemberShipPlanTradeIndex() {
        return tradeRepository.queryAiMemberShipPlanTradeIndex();
    }

    @Override
    public List<AIRechargePackEntity> queryAiRechargePackTradeIndex() {
        return tradeRepository.queryAiRechargePackTradeIndex();
    }
}
