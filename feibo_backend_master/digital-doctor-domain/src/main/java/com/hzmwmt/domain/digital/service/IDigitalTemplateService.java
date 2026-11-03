package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalTemplateEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalTemplateService
 * @Date: 2025/9/24 20:53
 */
public interface IDigitalTemplateService {
    CursorPageResponseData<DigitalTemplateEntity> queryUserTemplateIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalTemplateEntity> querySysTemplateIndex(Long lastCursorId, Integer size);

    void createUserDigitalTemplate(DigitalTemplateEntity entity);

    void deleteUserDigitalTemplate(DigitalTemplateEntity entity);

    void updateUserDigitalTemplateTitle(DigitalTemplateEntity entity);
}
