package com.hzmwmt.infrastructure.adapter.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import com.hzmwmt.infrastructure.dao.AIMembershipPlanMapper;
import com.hzmwmt.infrastructure.dao.AIRechargePackMapper;
import com.hzmwmt.infrastructure.dao.OrderMapper;
import com.hzmwmt.infrastructure.dao.po.Order;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Checkout must price the same explicitly configured product shown in the new catalogue. */
class TradeRepositoryProductSnapshotTest {
    private TradeRepository repository;
    private IEntitlementService entitlements;
    private OrderMapper orders;
    private AIMembershipPlanMapper oldPlans;
    private AIRechargePackMapper oldPacks;

    @BeforeEach void setUp() {
        repository=new TradeRepository();entitlements=mock(IEntitlementService.class);orders=mock(OrderMapper.class);
        oldPlans=mock(AIMembershipPlanMapper.class);oldPacks=mock(AIRechargePackMapper.class);
        ReflectionTestUtils.setField(repository,"entitlementService",entitlements);
        ReflectionTestUtils.setField(repository,"objectMapper",new ObjectMapper());
        ReflectionTestUtils.setField(repository,"orderMapper",orders);
        ReflectionTestUtils.setField(repository,"membershipPlanMapper",oldPlans);
        ReflectionTestUtils.setField(repository,"aiRechargePackMapper",oldPacks);
    }
    @Test void membershipAndRechargePricesUseConfiguredSnapshotWithSeparateTypes() {
        when(entitlements.snapshotProduct(false,5L)).thenReturn(snapshot("MEMBERSHIP","权益会员","29.90"));
        when(entitlements.snapshotProduct(true,5L)).thenReturn(snapshot("RECHARGE","视频充值","9.90"));
        var membership=repository.queryAiMembershipPlan(5L);
        var recharge=repository.queryAiRechargePack(5L);
        assertEquals(5L,membership.getAiMembershipPlanId());assertEquals("权益会员",membership.getName());
        assertEquals(0,new BigDecimal("29.90").compareTo(membership.getPrice()));assertEquals(30,membership.getDurationDays());
        assertEquals("视频充值",recharge.getName());assertEquals(0,new BigDecimal("9.90").compareTo(recharge.getPrice()));
        verifyNoInteractions(oldPlans,oldPacks);
    }
    @Test void disabledOrUnconfiguredProductFailsBeforeAnyOrderOrLegacyLookup() {
        when(entitlements.snapshotProduct(false,5L)).thenThrow(new AppException("ENTITLEMENT_INVALID","商品未启用或尚未配置可购买权益"));
        assertThrows(AppException.class,()->repository.queryAiMembershipPlan(5L));
        assertThrows(AppException.class,()->repository.saveOrder(order("29.90",ProductTypeEnumVO.MEMBERSHIP_PLAN)));
        verifyNoInteractions(oldPlans,oldPacks,orders);
    }
    @Test void priceChangeBetweenListingAndPersistenceRequiresRefreshingCheckout() {
        when(entitlements.snapshotProduct(false,5L)).thenReturn(snapshot("MEMBERSHIP","会员","39.90"));
        AppException exception=assertThrows(AppException.class,()->repository.saveOrder(order("29.90",ProductTypeEnumVO.MEMBERSHIP_PLAN)));
        assertTrue(exception.getInfo().contains("价格已更新"));verifyNoInteractions(orders);
    }
    @Test void freeProductCannotCreatePaymentOrderEvenIfServiceReturnsMalformedPurchasableData() {
        when(entitlements.snapshotProduct(false,5L)).thenReturn(snapshot("MEMBERSHIP","免费","0.00"));
        assertThrows(AppException.class,()->repository.saveOrder(order("0.00",ProductTypeEnumVO.MEMBERSHIP_PLAN)));
        verifyNoInteractions(orders);
    }
    @Test void successfulOrderPersistsExactEntitlementSnapshotAndResourceIdentity() {
        String immutable=snapshot("RECHARGE","充值","9.90");
        when(entitlements.snapshotProduct(true,5L)).thenReturn(immutable);
        when(orders.insert(any(Order.class))).thenReturn(1);
        Long id=repository.saveOrder(order("9.90",ProductTypeEnumVO.RECHARGE_PACK));
        ArgumentCaptor<Order> saved=ArgumentCaptor.forClass(Order.class);verify(orders).insert(saved.capture());
        assertEquals(id,saved.getValue().getOrderId());assertEquals(101L,saved.getValue().getUserId());
        assertEquals(5L,saved.getValue().getProductId());assertEquals((byte)1,saved.getValue().getProductType());
        assertEquals(immutable,saved.getValue().getEntitlementSnapshot());assertEquals(new BigDecimal("9.90"),saved.getValue().getTotalAmount());
        var sequence=inOrder(orders);sequence.verify(orders).releaseExpiredCheckoutLocks(101L,5L);sequence.verify(orders).insert(any(Order.class));
    }
    @Test void unchangedPendingOrderIsReusedOnlyWithinMatchingProductType() {
        String snapshot=snapshot("RECHARGE","充值","9.90");
        when(orders.selectUnPayOrderByUserIdAndProductId(101L,5L,1)).thenReturn(pending(snapshot,(byte)1));
        when(entitlements.snapshotProduct(true,5L)).thenReturn(snapshot);
        OrderEntity reused=repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.RECHARGE_PACK);
        assertEquals(808L,reused.getOrderId());assertEquals("trx-original",reused.getExternalTransactionId());
        assertEquals(ProductTypeEnumVO.RECHARGE_PACK,reused.getProductTypeEnumVO());
        assertNull(repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.MEMBERSHIP_PLAN));
        verify(orders).selectUnPayOrderByUserIdAndProductId(101L,5L,0);
        verify(entitlements,never()).snapshotProduct(false,5L);
    }
    @Test void pendingPriceOrEntitlementChangesRequireResolvingOriginalOrderFirst() {
        String original=snapshot("MEMBERSHIP","会员","29.90");
        when(orders.selectUnPayOrderByUserIdAndProductId(101L,5L,0)).thenReturn(pending(original,(byte)0));
        when(entitlements.snapshotProduct(false,5L)).thenReturn(snapshot("MEMBERSHIP","会员","39.90"));
        assertThrows(AppException.class,()->repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.MEMBERSHIP_PLAN));
        when(entitlements.snapshotProduct(false,5L)).thenReturn(original.replace("\"aiVideoSeconds\":60","\"aiVideoSeconds\":120"));
        assertThrows(AppException.class,()->repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.MEMBERSHIP_PLAN));
        verify(orders,never()).insert(any(Order.class));
    }
    @Test void historicalPendingOrderWithoutSnapshotCannotAcquireNewTerms() {
        when(orders.selectUnPayOrderByUserIdAndProductId(101L,5L,0)).thenReturn(pending(null,(byte)0));
        assertThrows(AppException.class,()->repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.MEMBERSHIP_PLAN));
        verifyNoInteractions(entitlements);
    }
    @Test void noValidUnpaidOrderDoesNotConsultProductOrReturnStalePaymentDetails() {
        when(orders.selectUnPayOrderByUserIdAndProductId(101L,5L,0)).thenReturn(null);
        assertNull(repository.queryUnPayOrder(101L,5L,ProductTypeEnumVO.MEMBERSHIP_PLAN));
        verifyNoInteractions(entitlements);
    }
    private Order pending(String snapshot,byte type) {
        return Order.builder().orderId(808L).userId(101L).productId(5L).productType(type).status(1)
                .externalTransactionId("trx-original").url("https://pay.example/order808").totalAmount(new BigDecimal("9.90"))
                .entitlementSnapshot(snapshot).validTime(new Date(System.currentTimeMillis()+900000)).createTime(new Date()).build();
    }
    private OrderEntity order(String price,ProductTypeEnumVO type) {
        return OrderEntity.builder().userId(101L).productId(5L).productTypeEnumVO(type).productName("购买商品")
                .totalAmount(new BigDecimal(price)).createTime(new Date()).validTime(new Date(System.currentTimeMillis()+900000)).build();
    }
    private String snapshot(String type,String name,String price) {
        return "{\"version\":1,\"productId\":\"5\",\"productType\":\""+type+"\",\"name\":\""+name+"\",\"planName\":\""+name+"\",\"price\":"+price+",\"durationDays\":30,\"premiumChatDailyLimit\":20,\"digitalHumanSeconds\":300,\"aiVideoSeconds\":60,\"premiumChatCredits\":0}";
    }
}
