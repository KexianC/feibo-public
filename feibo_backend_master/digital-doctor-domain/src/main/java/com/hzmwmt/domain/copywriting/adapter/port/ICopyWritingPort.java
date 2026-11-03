package com.hzmwmt.domain.copywriting.adapter.port;

import com.hzmwmt.domain.copywriting.model.entity.MessageEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICopyWritingPort
 * @Date: 2025/9/7 17:04
 */
public interface ICopyWritingPort {
    void sendApprovalStateMessage(MessageEntity messageEntity);
}
