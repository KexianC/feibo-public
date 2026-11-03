package com.hzmwmt.domain.message.service;

import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IMessageService
 * @Date: 2025/9/11 23:45
 */
public interface IMessageService {
    void sendNotifyMessage(MessageEntity messageEntity);

    CursorPageResponseData<MessageEntity> queryReadMessage(Long userId, Long lastCursorId, Integer size);
}
