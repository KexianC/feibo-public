package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.trade.model.entity.PaySettlementRuleCommandEntity;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory.DynamicContext;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RootNode
 * @Date: 2025/9/2 12:24
 */
@Slf4j
@Service
public class RootNode extends AbstractPaymentNotifySupport {
    private final ObjectMapper objectMapper;
    private final SecurityNode securityNode;

    public RootNode(SecurityNode securityNode, ObjectMapper objectMapper) {
        this.securityNode = securityNode;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> payload, DynamicContext context) throws Exception {
        PaySettlementRuleCommandEntity command = objectMapper.convertValue(payload, PaySettlementRuleCommandEntity.class);
        log.info("支付通知规则树-入口节点 | cusorderid:{}", command.getCusOrderId());
        if (StringUtils.isBlank(command.getSign()) || StringUtils.isBlank(command.getTrxId())
                || StringUtils.isBlank(command.getCusOrderId()) || StringUtils.isBlank(command.getAppId())
                || StringUtils.isBlank(command.getCusId()) || StringUtils.isBlank(command.getTrxStatus())
        ) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "缺少必要的通知参数");
        }
        context.setCommand(command);
        return router(payload, context);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> payload, DynamicContext context) {
        return securityNode;
    }


}
