package com.hzmwmt.domain.digital.service.template;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalTemplateEntity;
import com.hzmwmt.domain.digital.service.IDigitalTemplateService;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalTemplateService
 * @Date: 2025/9/24 20:55
 */
@Service
public class DigitalTemplateService implements IDigitalTemplateService {
    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalTemplateEntity> queryUserTemplateIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserTemplateIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalTemplateEntity> querySysTemplateIndex(Long lastCursorId, Integer size) {
        return digitalRepository.querySysTemplateIndex(lastCursorId, size);
    }

    @Override
    public void createUserDigitalTemplate(DigitalTemplateEntity entity) {
        digitalRepository.createUserDigitalTemplate(entity);
    }

    @Override
    public void deleteUserDigitalTemplate(DigitalTemplateEntity entity) {
        int deleteCount = digitalRepository.deleteDigitalTemplate(entity);
        if (deleteCount == 0) {
            throw new AppException(ResponseCode.AUTHORIZATION_EXCEPTION);
        }
    }

    @Override
    public void updateUserDigitalTemplateTitle(DigitalTemplateEntity entity) {
        int updateCount = digitalRepository.updateDigitalTemplateTitle(entity);
        if (updateCount == 0) {
            throw new AppException(ResponseCode.AUTHORIZATION_EXCEPTION);
        }
    }
}
