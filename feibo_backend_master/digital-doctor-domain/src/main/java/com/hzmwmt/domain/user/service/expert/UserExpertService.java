package com.hzmwmt.domain.user.service.expert;

import com.hzmwmt.domain.user.adapter.repository.IUserRepository;
import com.hzmwmt.domain.user.model.entity.RegisterAssistantCommandEntity;
import com.hzmwmt.domain.user.model.entity.UserEntity;
import com.hzmwmt.domain.user.service.IUserExpertService;
import com.hzmwmt.types.common.OffsetPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserExpertService
 * @Date: 2025/9/6 16:34
 */
@Service
public class UserExpertService implements IUserExpertService {
    @Resource
    private IUserRepository userRepository;

    @Override
    public OffsetPageResponseData<UserEntity> queryAssistantConfig(Long userId, Integer page, Integer size) {
        return userRepository.queryAssistantConfig(userId, page, size);
    }

    @Override
    public void registerAssistant(RegisterAssistantCommandEntity registerAssistantCommandEntity) {
        userRepository.registerAssistant(registerAssistantCommandEntity);
    }

}
