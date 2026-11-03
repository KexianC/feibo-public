package com.hzmwmt.trigger;

import com.hzmwmt.domain.trade.service.ITradeLockOrderService;
import com.hzmwmt.domain.trade.service.ITradeSettlementOrderService;
import com.hzmwmt.trigger.job.NoPayNotifyOrderJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class TradePaymentRecoveryTest {
    @Test void aPoisonedOrderCannotPreventLaterPaidOrdersFromBeingFulfilled() throws Exception {
        ITradeLockOrderService lock = mock(ITradeLockOrderService.class);
        ITradeSettlementOrderService settlement = mock(ITradeSettlementOrderService.class);
        var job = new NoPayNotifyOrderJob();
        ReflectionTestUtils.setField(job,"lockOrderService",lock);
        ReflectionTestUtils.setField(job,"tradeSettlementOrderService",settlement);
        when(lock.queryNoPayNotifyTrxIds()).thenReturn(List.of("bad-query","bad-delivery","good"));
        when(lock.queryPaymentStatus("bad-query")).thenThrow(new IllegalStateException("query failed"));
        when(lock.queryPaymentStatus("bad-delivery")).thenReturn(Map.of("id","bad-delivery"));
        when(lock.queryPaymentStatus("good")).thenReturn(Map.of("id","good"));
        when(settlement.settlementPaymentOrder(Map.of("id","bad-delivery"))).thenThrow(new IllegalStateException("delivery failed"));
        job.exec();
        verify(settlement).settlementPaymentOrder(Map.of("id","good"));
    }
}
