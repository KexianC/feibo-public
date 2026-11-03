package com.hzmwmt.domain.user.service;

import com.hzmwmt.domain.user.model.entity.UserEntity;
import com.hzmwmt.types.common.OffsetPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserAssistantService
 * @Date: 2025/9/6 16:49
 */
public interface IUserAssistantService {
    OffsetPageResponseData<UserEntity> queryExpertConfig(Long userId, Integer page, Integer size);
}
