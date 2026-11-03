package com.hzmwmt.infrastructure.adapter.repository;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;
import com.hzmwmt.infrastructure.dao.OrderMapper;
import com.hzmwmt.infrastructure.dao.po.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeEntitlementSettlementTest {
    private JdbcTemplate db;
    private TradeRepository repository;
    private IEntitlementService entitlements;
    private volatile boolean failAfterGrant;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE test_orders(order_id BIGINT PRIMARY KEY, user_id BIGINT, status INT, snapshot VARCHAR(100))");
        db.execute("CREATE TABLE test_grants(order_id BIGINT PRIMARY KEY, user_id BIGINT, snapshot VARCHAR(100))");
        db.update("INSERT INTO test_orders VALUES(1,101,2,'immutable-terms')");
        OrderMapper mapper = mock(OrderMapper.class);
        when(mapper.lockOrderByOrderId(anyLong())).thenAnswer(call -> db.queryForObject(
                "SELECT * FROM test_orders WHERE order_id=? FOR UPDATE", (rs, n) -> Order.builder()
                        .orderId(rs.getLong("order_id")).userId(rs.getLong("user_id"))
                        .status(rs.getInt("status")).entitlementSnapshot(rs.getString("snapshot")).build(), call.<Long>getArgument(0)));
        when(mapper.updateOrderStatusToDealDone(anyLong())).thenAnswer(call ->
                db.update("UPDATE test_orders SET status=3 WHERE order_id=? AND status=2", call.<Long>getArgument(0)));
        entitlements = mock(IEntitlementService.class);
        doAnswer(call -> {
            db.update("INSERT INTO test_grants VALUES(?,?,?)", call.<Long>getArgument(1), call.<Long>getArgument(0), call.<String>getArgument(2));
            if (failAfterGrant) throw new IllegalStateException("simulated delivery failure");
            return null;
        }).when(entitlements).grantPaidOrder(anyLong(), anyLong(), anyString());
        TradeRepository target = new TradeRepository();
        ReflectionTestUtils.setField(target, "orderMapper", mapper);
        ReflectionTestUtils.setField(target, "entitlementService", entitlements);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        repository = (TradeRepository) proxy.getProxy();
    }
    private TradePaySuccessEntity event() { return TradePaySuccessEntity.builder().orderId(1L).userId(999L).productId(999L).build(); }
    @Test void failedDeliveryRollsBackBothGrantAndCompletionAndCanBeRetried() {
        failAfterGrant = true;
        assertThatThrownBy(() -> repository.settlementTradePayOrder(event())).hasMessageContaining("delivery failure");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM test_grants", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT status FROM test_orders", Integer.class)).isEqualTo(2);
        failAfterGrant = false; repository.settlementTradePayOrder(event());
        assertThat(db.queryForObject("SELECT status FROM test_orders", Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT user_id FROM test_grants", Long.class)).isEqualTo(101L);
        assertThat(db.queryForObject("SELECT snapshot FROM test_grants", String.class)).isEqualTo("immutable-terms");
    }
    @Test void concurrentDuplicateCallbacksGrantOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i=0;i<8;i++) futures.add(executor.submit(() -> { start.await(); repository.settlementTradePayOrder(event()); return null; }));
            start.countDown(); for (var result : futures) result.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM test_grants", Integer.class)).isEqualTo(1);
        verify(entitlements, times(1)).grantPaidOrder(101L, 1L, "immutable-terms");
    }
    @Test void unpaidAndHistoricalOrdersDoNotReceiveInventedEntitlements() {
        db.update("UPDATE test_orders SET status=1");
        assertThatThrownBy(() -> repository.settlementTradePayOrder(event())).hasMessageContaining("尚未支付");
        db.update("UPDATE test_orders SET status=2,snapshot=NULL");
        assertThatThrownBy(() -> repository.settlementTradePayOrder(event())).hasMessageContaining("历史订单");
        verifyNoInteractions(entitlements);
    }
}
