package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.copywriting.adapter.port.ICopyWritingPort;
import com.hzmwmt.domain.copywriting.model.entity.MessageEntity;
import com.hzmwmt.infrastructure.gateway.netty.INettyService;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopyWritingPort
 * @Date: 2025/9/9 10:17
 */
@Service
@Slf4j
public class CopyWritingPort implements ICopyWritingPort {
    @Resource
    private INettyService nettyService;

    @Override
    public void sendApprovalStateMessage(MessageEntity messageEntity) {
        log.info("发送消息给用户：{}", messageEntity.getUserId());
        nettyService.sendMessageToUser(
                messageEntity.getUserId(),
                messageEntity.getTitle(),
                messageEntity.getContent(),
                TimeAgoUtil.format(messageEntity.getCreateTime())
        );

    }
}
