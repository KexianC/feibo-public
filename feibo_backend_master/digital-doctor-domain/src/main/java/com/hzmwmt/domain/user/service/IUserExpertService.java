package com.hzmwmt.domain.user.service;

import com.hzmwmt.domain.user.model.entity.RegisterAssistantCommandEntity;
import com.hzmwmt.domain.user.model.entity.UserEntity;
import com.hzmwmt.types.common.OffsetPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserExpertService
 * @Date: 2025/9/6 16:33
 */
public interface IUserExpertService {
    OffsetPageResponseData<UserEntity> queryAssistantConfig(Long userId, Integer page, Integer size);

    void registerAssistant(RegisterAssistantCommandEntity registerAssistantCommandEntity);
}
