package com.hzmwmt.domain.user.adapter.port;

import com.hzmwmt.domain.user.model.entity.AIQuotaEntity;
import com.hzmwmt.domain.user.model.entity.UpdateAvatarCommandEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserPort
 * @Date: 2025/9/17 12:19
 */
public interface IUserPort {
    void sendUpdateShortMessage(String phoneNumber);

    void verifyUpdateVerificationCode(String phoneNumber, String code);

    void uploadAvatar(UpdateAvatarCommandEntity updateAvatarCommandEntity);

    void receiveAiQuota(String userId);

    AIQuotaEntity queryAiQuota(String userId);
}
