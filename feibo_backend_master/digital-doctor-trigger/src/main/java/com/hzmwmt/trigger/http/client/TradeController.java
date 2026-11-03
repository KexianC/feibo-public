package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.ITradeApi;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.api.dto.LockTradePayOrderRequestDTO;
import com.hzmwmt.api.dto.LockTradePayOrderResponseDTO;
import com.hzmwmt.api.dto.TradeProductResponseDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.trade.model.aggregate.AIMembershipPlanAggregate;
import com.hzmwmt.domain.trade.model.entity.AIRechargePackEntity;
import com.hzmwmt.domain.trade.model.entity.CreateOrderEntity;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PayOrderEntity;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import com.hzmwmt.domain.trade.service.ITradeIndexService;
import com.hzmwmt.domain.trade.service.ITradeLockOrderService;
import com.hzmwmt.domain.trade.service.ITradeSettlementOrderService;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradeController
 * @Date: 2025/8/27 16:42
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/trade/")
public class TradeController implements ITradeApi {

    @Resource
    private ITradeIndexService tradeIndexService;
    @Resource
    private ITradeLockOrderService tradeLockOrderService;
    @Resource
    private ITradeSettlementOrderService tradeSettlementOrderService;
    @Resource
    private IAuthService authService;
    @Resource
    private com.hzmwmt.domain.entitlement.IEntitlementAdminService entitlementAdminService;

    @Resource
    private com.hzmwmt.domain.trade.adapter.repository.ITradeRepository tradeRepository;

    @GetMapping("orders/{orderId}")
    public Response<java.util.Map<String, Object>> orderStatus(@PathVariable Long orderId) {
        Long userId = authService.queryValidatedUser().getUserId();
        OrderEntity order = tradeRepository.queryOrderByOrderId(orderId);
        if (order == null || !java.util.Objects.equals(order.getUserId(), userId)) {
            throw new com.hzmwmt.types.exception.AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单不存在");
        }
        return Response.<java.util.Map<String, Object>>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(java.util.Map.of(
                        "orderId", String.valueOf(order.getOrderId()),
                        "status", order.getOrderStatusEnumVO().getCode(),
                        "fulfilled", com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO.DEAL_DONE.equals(order.getOrderStatusEnumVO())))
                .build();
    }


    @Override
    @RequestMapping(value = "index", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询交易首页商品列表", recordParams = true)
    @SuppressWarnings("unchecked")
    public Response<TradeProductResponseDTO> queryTradeIndex() {
        java.util.Map<String, Object> catalogue = entitlementAdminService.configuration(true);
        List<java.util.Map<String, Object>> plans = (List<java.util.Map<String, Object>>) catalogue.get("plans");
        List<java.util.Map<String, Object>> packs = (List<java.util.Map<String, Object>>) catalogue.get("rechargePacks");
        List<TradeProductResponseDTO.MembershipPlan> planList = plans.stream().map(p ->
                TradeProductResponseDTO.MembershipPlan.builder()
                        .aiMembershipPlanId(Long.valueOf(p.get("productId").toString()))
                        .planCode("PLAN_" + p.get("productId")).name(p.get("name").toString())
                        .price(new java.math.BigDecimal(p.get("price").toString()))
                        .durationDays(((Number) p.get("durationDays")).intValue())
                        .features(java.util.List.of(
                                feature("PREMIUM_CHAT", "高级对话（次/天）", p.get("premiumChatDailyLimit")),
                                feature("DIGITAL_HUMAN_SECONDS", "数字人合成（秒）", p.get("digitalHumanSeconds")),
                                feature("AI_VIDEO_SECONDS", "AI视频（秒）", p.get("aiVideoSeconds")),
                                feature("PREMIUM_CHAT_CREDITS", "高级对话额外次数", p.get("premiumChatCredits"))))
                        .build()).toList();
        List<TradeProductResponseDTO.RechargePack> packList = packs.stream().map(p ->
                TradeProductResponseDTO.RechargePack.builder()
                        .aiRechargePackId(Long.valueOf(p.get("productId").toString()))
                        .packCode("PACK_" + p.get("productId")).name(p.get("name").toString())
                        .price(new java.math.BigDecimal(p.get("price").toString())).value(1)
                        .description("数字人 " + p.get("digitalHumanSeconds") + " 秒；AI视频 " + p.get("aiVideoSeconds")
                                + " 秒；高级对话额外 " + p.get("premiumChatCredits") + " 次")
                        .aiFeatureTemplateVO(TradeProductResponseDTO.AIFeatureTemplate.builder()
                                .templateCode("ENTITLEMENT_PACK").name("权益包").description("按购买的各项权益分别到账").build())
                        .build()).toList();
        return Response.<TradeProductResponseDTO>builder().code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo()).data(TradeProductResponseDTO.builder()
                        .membershipPlans(planList).rechargePacks(packList).build()).build();
    }

    private TradeProductResponseDTO.AIFeature feature(String code, String name, Object value) {
        return TradeProductResponseDTO.AIFeature.builder().aiFeatureId((long) Math.abs(code.hashCode()))
                .value(value == null ? 0 : ((Number) value).intValue())
                .aiFeatureTemplate(TradeProductResponseDTO.AIFeatureTemplate.builder()
                        .templateCode(code).name(name).description(name).build()).build();
    }
    @Override
    @RequestMapping(value = "lock_trade_pay_order", method = RequestMethod.POST)
    @OperationLog(operationType = "创建", description = "锁定交易支付订单", recordParams = true)
    public Response<LockTradePayOrderResponseDTO> lockTradePayOrder(@RequestBody LockTradePayOrderRequestDTO lockTradePayOrderRequestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        Long productId = lockTradePayOrderRequestDTO.getProductId();
        log.info("{}用户开始下单: {}", userId, productId);
        PayOrderEntity payOrderEntity = tradeLockOrderService.lockTradeOrder(CreateOrderEntity.builder()
                .userId(userId)
                .productId(productId)
                .productTypeEnumVO(ProductTypeEnumVO.getByCode(lockTradePayOrderRequestDTO.getProductType()))
                .build()
        );
        return Response.<LockTradePayOrderResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(LockTradePayOrderResponseDTO.builder()
                        .url(payOrderEntity.getUrl())
                        .trxId(payOrderEntity.getTrxId())
                        .orderId(payOrderEntity.getOrderId())
                        .build()
                )
                .build();

    }

    @Override
    @RequestMapping(value = "payment_success_handling", method = RequestMethod.POST)
    @OperationLog(operationType = "处理", description = "支付成功处理", recordParams = true)
    public String paymentSuccessHandling(@RequestParam Map<String, String> allParams) throws Exception {
        PaymentSettlementEntity settlementEntity = tradeSettlementOrderService.settlementPaymentOrder(allParams);
        return settlementEntity.getMessage();
    }


}
