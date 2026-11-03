package com.hzmwmt.domain.trade.service.lock;

import com.hzmwmt.domain.trade.adapter.port.ITradePort;
import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.model.entity.*;
import com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import com.hzmwmt.domain.trade.service.ITradeLockOrderService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradeLockOrderService
 * @Date: 2025/8/26 16:14
 */
@Slf4j
@Service
public class TradeLockOrderService implements ITradeLockOrderService {
    @Resource
    private ITradeRepository tradeRepository;
    @Resource
    private ITradePort tradePort;

    @Override
    public PayOrderEntity lockTradeOrder(CreateOrderEntity createOrderEntity) {
        Long productId = createOrderEntity.getProductId();
        Long userId = createOrderEntity.getUserId();
        if (userId == null || productId == null || createOrderEntity.getProductTypeEnumVO() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "请选择有效商品");
        }
        OrderEntity unpaidOrderEntity = tradeRepository.queryUnPayOrder(userId, productId, createOrderEntity.getProductTypeEnumVO());
        if (null != unpaidOrderEntity && OrderStatusEnumVO.PAY_WAIT.equals(unpaidOrderEntity.getOrderStatusEnumVO())) {
            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", userId, productId, unpaidOrderEntity.getOrderId());
            return PayOrderEntity.builder()
                    .trxId(unpaidOrderEntity.getExternalTransactionId())
                    .url(unpaidOrderEntity.getUrl())
                    .orderId(unpaidOrderEntity.getOrderId())
                    .build();
        } else if (null != unpaidOrderEntity && OrderStatusEnumVO.CREATE.equals(unpaidOrderEntity.getOrderStatusEnumVO())) {
            log.info("创建订单-存在，存在未创建支付单订单，创建支付单开始 userId:{} productId:{} orderId:{}", userId, productId, unpaidOrderEntity.getOrderId());
            //调用支付接口
            PayOrderEntity payOrderEntity = tradePort.requestPayment(unpaidOrderEntity);
            //修改订单状态
            unpaidOrderEntity.setUrl(payOrderEntity.getUrl());
            unpaidOrderEntity.setExternalTransactionId(payOrderEntity.getTrxId());
            int count = tradeRepository.updateOrderStatus2WaitPay(unpaidOrderEntity);
            if (count != 1) {
                log.error("更新订单等待支付状态失败 | orderId:{}", unpaidOrderEntity.getOrderId());
                throw new AppException(ResponseCode.UPDATE_ZERO.getCode());
            }
            return payOrderEntity;
        }

        Long dbProductId;
        BigDecimal price;
        String productName;
        ProductTypeEnumVO productType = createOrderEntity.getProductTypeEnumVO();
        if (productType == ProductTypeEnumVO.MEMBERSHIP_PLAN) {
            AIMembershipPlanEntity product = tradeRepository.queryAiMembershipPlan(createOrderEntity.getProductId());
            dbProductId = product.getAiMembershipPlanId();
            price = product.getPrice();
            productName = product.getName();
        } else {
            AIRechargePackEntity product = tradeRepository.queryAiRechargePack(createOrderEntity.getProductId());
            dbProductId = product.getAiRechargePackId();
            price = product.getPrice();
            productName = product.getName();
        }
        if (price == null || price.signum() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "免费权益自动生效，无需购买");
        }
        Date nowTime = new Date();
        OrderEntity orderEntity = OrderEntity.builder()
                .userId(userId)
                .productId(dbProductId)
                .totalAmount(price)
                .productName(productName)
                .productTypeEnumVO(productType)
                .createTime(nowTime)
                .build();
        orderEntity.setValidTime(orderEntity.generateValidTime(nowTime));
        Long orderId = tradeRepository.saveOrder(orderEntity);
        orderEntity.setOrderId(orderId);
        //调用支付接口
        PayOrderEntity payOrderEntity = tradePort.requestPayment(orderEntity);
        //修改订单状态
        orderEntity.setUrl(payOrderEntity.getUrl());
        orderEntity.setExternalTransactionId(payOrderEntity.getTrxId());
        int count = tradeRepository.updateOrderStatus2WaitPay(orderEntity);
        if (count != 1) {
            log.error("更新订单等待支付状态失败 | orderId:{}", orderId);
            throw new AppException(ResponseCode.UPDATE_ZERO.getCode());
        }
        return payOrderEntity;
    }

    /***
     * @Author huihui
     * @Description //TODO 查询所有未成功支付订单用于一致性补偿
     * @Param []
     * @return java.util.List<java.lang.Long>
     * @Date 2025/9/1 23:48
     **/
    @Override
    public List<String> queryNoPayNotifyTrxIds() {
        return tradeRepository.queryNoPayNotifyTrxIds();
    }

    @Override
    public Map<String, String> queryPaymentStatus(String trxId) {
        tradeRepository.markPaymentQueryAttempt(trxId);
        return tradePort.queryPaymentStatus(trxId);
    }


}
