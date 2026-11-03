package com.hzmwmt.infrastructure.adapter.repository;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.model.aggregate.AIMembershipPlanAggregate;
import com.hzmwmt.domain.trade.model.entity.*;
import com.hzmwmt.domain.trade.model.valobj.AIFeatureTemplateVO;
import com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.*;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;



/**
 * @author huihui
 * @version v1.0
 * @Description: 交易仓储实现类（包含有效期叠加与事务优化）
 */
@Slf4j
@Repository
public class TradeRepository extends AbstractRepository implements ITradeRepository {
    @Resource
    private AIPlanFeatureMapMapper aiPlanFeatureMapMapper;
    @Resource
    private AIMembershipPlanMapper membershipPlanMapper;
    @Resource
    private AIFeatureMapper aiFeatureMapper;
    @Resource
    private AIFeatureTemplateMapper aiFeatureTemplateMapper;
    @Resource
    private AIRechargePackMapper aiRechargePackMapper;
    @Resource
    private OrderMapper orderMapper;

    // 引入内部服务处理权益发放，解决事务失效问题
    @Resource
    private IEntitlementService entitlementService;
    @Resource
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ... queryAiMemberShipPlanTradeIndex, queryAiRechargePackTradeIndex 等查询方法保持不变 ...
    // 为节省篇幅，此处省略查询方法的具体实现，请保留原有的查询逻辑

    @Override
    public List<AIMembershipPlanAggregate> queryAiMemberShipPlanTradeIndex() {
        // 查询有效的会员套餐
        List<AIMembershipPlan> membershipPlans = membershipPlanMapper.selectValidMembershipPlan();
        List<AIMembershipPlanAggregate> result = new ArrayList<>();

        for (AIMembershipPlan plan : membershipPlans) {
            // 查询套餐关联的权益ID列表
            List<Long> featureIds = aiPlanFeatureMapMapper.selectAiFeatureIdsByMembershipPlanId(plan.getAiMembershipPlanId());
            List<AIFeatureEntity> featureEntities = new ArrayList<>();

            for (Long featureId : featureIds) {
                // 查询权益详情
                AIFeature feature = aiFeatureMapper.selectById(featureId);
                if (feature != null) {
                    // 查询权益模板
                    AIFeatureTemplate template = aiFeatureTemplateMapper.selectById(feature.getAiFeatureTemplateId());
                    if (template != null) {
                        // 构建权益模板VO
                        AIFeatureTemplateVO templateVO = AIFeatureTemplateVO.builder()
                                .templateCode(template.getTemplateCode())
                                .name(template.getName())
                                .description(template.getDescription())
                                .build();

                        // 构建权益实体
                        AIFeatureEntity featureEntity = AIFeatureEntity.builder()
                                .aiFeatureId(feature.getAiFeatureId())
                                .value(feature.getValue())
                                .aiFeatureTemplateVO(templateVO)
                                .build();

                        featureEntities.add(featureEntity);
                    }
                }
            }

            // 构建会员套餐实体
            AIMembershipPlanEntity planEntity = AIMembershipPlanEntity.builder()
                    .aiMembershipPlanId(plan.getAiMembershipPlanId())
                    .planCode(plan.getPlanCode())
                    .name(plan.getName())
                    .price(plan.getPrice())
                    .durationDays(plan.getDurationDays())
                    .build();

            // 构建套餐聚合
            AIMembershipPlanAggregate aggregate = AIMembershipPlanAggregate.builder()
                    .aiMembershipPlanEntity(planEntity)
                    .aiFeatureEntities(featureEntities)
                    .build();

            result.add(aggregate);
        }

        return result;
    }

    @Override
    public List<AIRechargePackEntity> queryAiRechargePackTradeIndex() {
        // 查询有效的充值包
        List<AIRechargePack> rechargePacks = aiRechargePackMapper.selectValidAiRechargePack();
        List<AIRechargePackEntity> result = new ArrayList<>();

        for (AIRechargePack pack : rechargePacks) {
            // 查询充值包关联的模板
            AIFeatureTemplate template = aiFeatureTemplateMapper.selectById(pack.getAiFeatureTemplateId());
            if (template != null) {
                // 构建权益模板VO
                AIFeatureTemplateVO templateVO = AIFeatureTemplateVO.builder()
                        .templateCode(template.getTemplateCode())
                        .name(template.getName())
                        .description(template.getDescription())
                        .build();

                // 构建充值包实体
                AIRechargePackEntity packEntity = AIRechargePackEntity.builder()
                        .aiRechargePackId(pack.getAiRechargePackId())
                        .packCode(pack.getPackCode())
                        .name(pack.getName())
                        .description(pack.getDescription())
                        .value(pack.getValue())
                        .price(pack.getPrice())
                        .aiFeatureTemplateVO(templateVO)
                        .build();

                result.add(packEntity);
            }
        }

        return result;
    }

    @Override
    public OrderEntity queryUnPayOrder(Long userId, Long productId, ProductTypeEnumVO productType) {
        Order order = orderMapper.selectUnPayOrderByUserIdAndProductId(userId, productId, productType == ProductTypeEnumVO.RECHARGE_PACK ? 1 : 0);
        if (null != order) {
            if (order.getEntitlementSnapshot() == null || order.getEntitlementSnapshot().isBlank()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "已有历史待支付订单，请核对原订单或等待其过期后重新购买");
            }
            try {
                var currentTerms = paidProduct(productType == ProductTypeEnumVO.RECHARGE_PACK, productId);
                if (!objectMapper.readTree(order.getEntitlementSnapshot()).equals(currentTerms)) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "已有价格或权益不同的待支付订单，请完成原订单或等待其过期后重新购买");
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "原订单权益需核对后处理");
            }
            return OrderEntity.builder()
                    .orderId(order.getOrderId())
                    .userId(order.getUserId())
                    .externalTransactionId(order.getExternalTransactionId())
                    .productId(order.getProductId())
                    .url(order.getUrl())
                    .totalAmount(order.getTotalAmount())
                    .productName(order.getProductName())
                    .productTypeEnumVO(ProductTypeEnumVO.getByCode(order.getProductType() != null && order.getProductType() == 1))
                    .validTime(order.getValidTime())
                    .createTime(order.getCreateTime())
                    .orderStatusEnumVO(OrderStatusEnumVO.getByCode(order.getStatus()))
                    .build();
        }
        return null;
    }

    @Override
    public AIMembershipPlanEntity queryAiMembershipPlan(Long productId) {
        var product = paidProduct(false, productId);
        return AIMembershipPlanEntity.builder().aiMembershipPlanId(productId)
                .planCode("PLAN_" + productId).name(product.path("name").asText())
                .price(product.path("price").decimalValue()).durationDays(product.path("durationDays").asInt()).build();
    }

    @Override
    public AIRechargePackEntity queryAiRechargePack(Long productId) {
        var product = paidProduct(true, productId);
        return AIRechargePackEntity.builder().aiRechargePackId(productId)
                .packCode("PACK_" + productId).name(product.path("name").asText())
                .price(product.path("price").decimalValue()).build();
    }

    private com.fasterxml.jackson.databind.JsonNode paidProduct(boolean recharge, Long productId) {
        try { return objectMapper.readTree(entitlementService.snapshotProduct(recharge, productId)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商品权益配置无效");
        }
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrder(OrderEntity orderEntity) {
        String snapshot = entitlementService.snapshotProduct(orderEntity.getProductTypeEnumVO() == ProductTypeEnumVO.RECHARGE_PACK, orderEntity.getProductId());
        try {
            java.math.BigDecimal price = objectMapper.readTree(snapshot).path("price").decimalValue();
            if (price.signum() <= 0 || orderEntity.getTotalAmount() == null || price.compareTo(orderEntity.getTotalAmount()) != 0) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商品价格已更新，请刷新后重新下单");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商品权益配置无效");
        }
        long orderId = IdWorker.getId();
        orderMapper.releaseExpiredCheckoutLocks(orderEntity.getUserId(), orderEntity.getProductId());
        try {
            orderMapper.insert(Order.builder()
                    .orderId(orderId)
                    .userId(orderEntity.getUserId())
                    .productId(orderEntity.getProductId())
                    .totalAmount(orderEntity.getTotalAmount())
                    .productName(orderEntity.getProductName())
                    .entitlementSnapshot(snapshot)
                    .productType(orderEntity.getProductTypeEnumVO() != null && orderEntity.getProductTypeEnumVO().getCode() != null ? (byte) (orderEntity.getProductTypeEnumVO().getCode() ? 1 : 0) : null)
                    .validTime(orderEntity.getValidTime())
                    .createTime(orderEntity.getCreateTime())
                    .creatingLock((byte) 1)
                    .build()
            );
        } catch (Exception e) {
            throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(), "请勿重复下单");
        }
        return orderId;
    }

    @Override
    public List<String> queryNoPayNotifyTrxIds() {
        return orderMapper.selectNoPayNotifyTrxIds();
    }
    @Override public void markPaymentQueryAttempt(String trxId) { orderMapper.markPaymentQueryAttempt(trxId); }

    @Override
    public OrderEntity queryOrderByOrderId(Long orderId) {
        Order order = orderMapper.selectOrderByOrderId(orderId);
        if (null != order) {
            return OrderEntity.builder()
                    .orderId(order.getOrderId())
                    .userId(order.getUserId())
                    .externalTransactionId(order.getExternalTransactionId())
                    .productId(order.getProductId())
                    .url(order.getUrl())
                    .totalAmount(order.getTotalAmount())
                    .productName(order.getProductName())
                    .productTypeEnumVO(order.getProductType() != null ? ProductTypeEnumVO.getByIntegerCode(order.getProductType().intValue()) : null)
                    .validTime(order.getValidTime())
                    .createTime(order.getCreateTime())
                    .orderStatusEnumVO(OrderStatusEnumVO.getByCode(order.getStatus()))
                    .build();
        }
        return null;
    }

    @Override
    public int updateOrderStatus2WaitPay(OrderEntity orderEntity) {
        return orderMapper.updateOrderStatusToWaitPay(
                Order.builder()
                        .orderId(orderEntity.getOrderId())
                        .externalTransactionId(orderEntity.getExternalTransactionId())
                        .url(orderEntity.getUrl())
                        .build()
        );
    }

    @Override
    public int updateOrderStatusToClosedByFailure(long orderId) {
        return orderMapper.updateOrderStatusToClosedByFailure(orderId);
    }

    @Override
    public int updateOrderStatusToPaySuccess(OrderEntity orderEntity) {
        return orderMapper.updateOrderStatusToPaySuccess(
                Order.builder()
                        .orderId(orderEntity.getOrderId())
                        .payTime(orderEntity.getPayTime())
                        .build()
        );
    }

    @Override
    public int updateOrderStatusToClosedAfterRefund(long orderId) {
        return orderMapper.updateOrderStatusToClosedAfterRefund(orderId);
    }

    @Override
    public int updateOrderStatusToRefundWait(long orderId) {
        return orderMapper.updateOrderStatusToRefundWait(orderId);
    }

    /** Order completion and entitlement delivery share one database transaction. */
    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void settlementTradePayOrder(TradePaySuccessEntity event) {
        Order order = orderMapper.lockOrderByOrderId(event.getOrderId());
        if (order == null) throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单不存在");
        if (Integer.valueOf(OrderStatusEnumVO.DEAL_DONE.getCode()).equals(order.getStatus())) return;
        if (!Integer.valueOf(OrderStatusEnumVO.PAY_SUCCESS.getCode()).equals(order.getStatus())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单尚未支付成功");
        }
        if (order.getEntitlementSnapshot() == null || order.getEntitlementSnapshot().isBlank()) {
            // Never guess the paid terms of a pre-migration order.
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "历史订单权益需核对后补发");
        }
        // Identity and terms come from the locked order, never the event payload.
        entitlementService.grantPaidOrder(order.getUserId(), order.getOrderId(), order.getEntitlementSnapshot());
        if (orderMapper.updateOrderStatusToDealDone(order.getOrderId()) != 1) throw new AppException(ResponseCode.UPDATE_ZERO);
    }
}
