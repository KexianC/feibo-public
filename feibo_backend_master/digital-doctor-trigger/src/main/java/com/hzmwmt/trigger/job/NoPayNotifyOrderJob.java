package com.hzmwmt.trigger.job;

import com.hzmwmt.domain.trade.service.ITradeLockOrderService;
import com.hzmwmt.domain.trade.service.ITradeSettlementOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 有空可以用MQ优化
 * @ClassName NoPayNotifyOrderJob
 * @Date: 2025/9/1 13:18
 */
@Slf4j
@Service
public class NoPayNotifyOrderJob {
    @Resource
    private ITradeLockOrderService lockOrderService;
    @Resource
    private ITradeSettlementOrderService tradeSettlementOrderService;

    @Scheduled(cron = "0 * * * * ?")
    public void exec() {
        try {
            log.info("定时任务，检测未接收到或未正确处理的支付回调通知");
            List<String> trxIds = lockOrderService.queryNoPayNotifyTrxIds();
            if (null == trxIds || trxIds.isEmpty()) return;
            for (String trxId : trxIds) {
                try {
                log.info("定时任务，检测未接收到或未正确处理的支付回调通知 trxId: {}", trxId);
                Map<String, String> stringStringMap = lockOrderService.queryPaymentStatus(trxId);
                tradeSettlementOrderService.settlementPaymentOrder(stringStringMap);
                } catch (Exception e) {
                    log.error("支付订单补偿失败，稍后重试；trxId={}, type={}", trxId, e.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            log.error("检测未接收到或未正确处理的支付回调通知失败: type={}", e.getClass().getSimpleName());
        }
    }
}
