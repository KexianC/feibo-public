package com.hzmwmt.domain.message.adapter.repository;

import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ImessageRepository
 * @Date: 2025/9/11 23:43
 */
public interface ImessageRepository {
    CursorPageResponseData<MessageEntity> queryReadMessage(Long userId, Long lastCursorId, Integer size);

    void saveNotifyMessage(MessageEntity messageEntity);

    void updateNotifyMessage2Read(Long messageId);
}
