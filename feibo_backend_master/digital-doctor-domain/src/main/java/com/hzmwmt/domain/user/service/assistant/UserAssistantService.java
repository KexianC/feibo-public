package com.hzmwmt.domain.user.service.assistant;

import com.hzmwmt.domain.user.adapter.repository.IUserRepository;
import com.hzmwmt.domain.user.model.entity.UserEntity;
import com.hzmwmt.domain.user.service.IUserAssistantService;
import com.hzmwmt.types.common.OffsetPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAssistantService
 * @Date: 2025/9/6 16:51
 */
@Service
public class UserAssistantService implements IUserAssistantService {
    @Resource
    private IUserRepository userRepository;

    @Override
    public OffsetPageResponseData<UserEntity> queryExpertConfig(Long userId, Integer page, Integer size) {
        return userRepository.queryExpertConfig(userId, page, size);
    }
}
