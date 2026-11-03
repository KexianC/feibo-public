package com.hzmwmt.domain.message.service;

import com.hzmwmt.domain.message.adapter.port.IMessagePort;
import com.hzmwmt.domain.message.adapter.repository.ImessageRepository;
import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MessageService
 * @Date: 2025/9/11 23:56
 */
@Slf4j
@Service
public class MessageService implements IMessageService {
    @Resource
    private ImessageRepository messageRepository;
    @Resource
    private IMessagePort messagePort;


    @Override
    public void sendNotifyMessage(MessageEntity messageEntity) {
        messageRepository.saveNotifyMessage(messageEntity);
        if (messagePort.sendNotifyMessage(messageEntity)) {
            log.info("消息：{}发送成功", messageEntity.getMessageId());
            messageRepository.updateNotifyMessage2Read(messageEntity.getMessageId());
        }
    }

    @Override
    public CursorPageResponseData<MessageEntity> queryReadMessage(Long userId, Long lastCursorId, Integer size) {
        return messageRepository.queryReadMessage(userId, lastCursorId, size);
    }
}
