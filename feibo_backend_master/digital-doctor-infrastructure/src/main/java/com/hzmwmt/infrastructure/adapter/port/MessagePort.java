package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.message.adapter.port.IMessagePort;
import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.infrastructure.gateway.netty.INettyService;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MessagePort
 * @Date: 2025/9/12 0:22
 */
@Service
public class MessagePort implements IMessagePort {
    @Resource
    private INettyService nettyService;

    @Override
    public boolean sendNotifyMessage(MessageEntity messageEntity) {
        return nettyService.sendMessageToUser(messageEntity.getUserId(), messageEntity.getTitle(), messageEntity.getContent(), TimeAgoUtil.format(messageEntity.getCreateTime()));
    }
}
