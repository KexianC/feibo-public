package com.hzmwmt.trigger.listener;

import com.google.common.eventbus.Subscribe;
import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.domain.message.service.IMessageService;
import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.event.PaySuccessMessageEvent;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import com.hzmwmt.domain.trade.service.ITradeSettlementOrderService;
import com.hzmwmt.infrastructure.gateway.netty.INettyService;
import com.hzmwmt.types.util.SpringUtil;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.hzmwmt.types.common.Constants.SysMessageConstants.TREAD_TITLE;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OrderPaySuccessListener
 * @Date: 2025/9/1 13:22
 */
@Slf4j
@Component
public class OrderPaySuccessListener {

    @Resource
    private ITradeRepository tradeRepository;


    @Subscribe
    public void handleEvent(PaySuccessMessageEvent.PaySuccessMessage paySuccessMessage) {
        log.info("收到user:{}支付orderId：{}成功消息", paySuccessMessage.getUserId(), paySuccessMessage.getOrderId());
        try {
            //防止循环依赖
            //消息发送
            IMessageService messageService = SpringUtil.getBean(IMessageService.class);
            messageService.sendNotifyMessage(
                    MessageEntity.builder()
                            .userId(paySuccessMessage.getUserId())
                            .title(TREAD_TITLE)
                            .content(paySuccessMessage.getProductName() + "订单支付成功")
                            .createTime(new Date()) // 设置创建时间
                            .build()
            );

            // 支付成功时通过WebSocket向前端推送实时通知
            try {
                log.info("尝试获取NettyService并发送WebSocket消息给用户ID: {}", paySuccessMessage.getUserId());

                // 先检查Spring容器中是否存在NettyService Bean
                boolean nettyServiceExists = SpringUtil.getApplicationContext().containsBean("nettyService");
                log.info("Spring容器中是否存在nettyService Bean: {}", nettyServiceExists);

                INettyService nettyService = SpringUtil.getBean(INettyService.class);
                log.info("成功获取NettyService实例");

                if (nettyService != null) {
                    String title = "支付成功";
                    String content = paySuccessMessage.getProductName() + "订单支付成功";
                    String createTime = TimeAgoUtil.format(new Date());

                    log.info("准备发送WebSocket消息: userId={}, contentLength={}",
                            paySuccessMessage.getUserId(), content.length());

                    Boolean sendResult = nettyService.sendMessageToUser(
                            paySuccessMessage.getUserId(),
                            title,
                            content,
                            createTime
                    );

                    if (sendResult) {
                        log.info("WebSocket支付成功通知发送成功，用户ID: {}", paySuccessMessage.getUserId());
                    } else {
                        log.warn("WebSocket消息发送失败，可能用户不在线或连接已断开，用户ID: {}", paySuccessMessage.getUserId());
                    }
                } else {
                    log.warn("WebSocket服务未就绪，跳过支付通知推送");
                }
            } catch (Exception e) {
                // WebSocket通知失败不影响主要业务流程，仅记录日志
                log.error("发送WebSocket支付成功通知失败: type={}", e.getClass().getSimpleName());
            }

            // Entitlements were committed before this notification was published.
        } catch (Exception e) {
            log.error("订单支付成功消息处理异常: type={}", e.getClass().getSimpleName());
            //加一个event表处理异常情况
        }
    }
}

