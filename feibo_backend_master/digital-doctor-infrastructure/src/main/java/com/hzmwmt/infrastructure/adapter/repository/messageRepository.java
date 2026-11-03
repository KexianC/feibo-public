package com.hzmwmt.infrastructure.adapter.repository;

import com.hzmwmt.domain.message.adapter.repository.ImessageRepository;
import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.infrastructure.dao.PushMessageMapper;
import com.hzmwmt.infrastructure.dao.po.PushMessage;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName messageRepository
 * @Date: 2025/9/12 0:05
 */
@Repository
public class messageRepository extends AbstractRepository implements ImessageRepository {
    @Resource
    private PushMessageMapper pushMessageMapper;

    @Override
    public CursorPageResponseData<MessageEntity> queryReadMessage(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> pushMessageMapper.selectReadListCursorPageByUserId(userId, lastCursorId, size + 1),
                PushMessage::getPushMessageId,
                pushMessage -> MessageEntity.builder()
                        .userId(pushMessage.getUserId())
                        .messageId(pushMessage.getPushMessageId())
                        .title(pushMessage.getTitle())
                        .content(pushMessage.getContent())
                        .sendTime(pushMessage.getSendTime())
                        .build(),
                size
        );
    }

    @Override
    public void saveNotifyMessage(MessageEntity messageEntity) {
        PushMessage pushMessage = PushMessage.builder()
                .userId(messageEntity.getUserId())
                .title(messageEntity.getTitle())
                .content(messageEntity.getContent())
                .createTime(messageEntity.getCreateTime())
                .build();
        pushMessageMapper.insert(pushMessage);
        messageEntity.setMessageId(pushMessage.getPushMessageId());
        messageEntity.setCreateTime(pushMessage.getCreateTime());
    }

    @Override
    public void updateNotifyMessage2Read(Long messageId) {
        pushMessageMapper.updatePushMessage2Read(messageId);
    }
}
