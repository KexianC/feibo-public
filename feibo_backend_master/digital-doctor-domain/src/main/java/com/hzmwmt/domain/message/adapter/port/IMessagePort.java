package com.hzmwmt.domain.message.adapter.port;

import com.hzmwmt.domain.message.model.entity.MessageEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IMessagePort
 * @Date: 2025/9/11 23:57
 */
public interface IMessagePort {
    boolean sendNotifyMessage(MessageEntity messageEntity);
}
