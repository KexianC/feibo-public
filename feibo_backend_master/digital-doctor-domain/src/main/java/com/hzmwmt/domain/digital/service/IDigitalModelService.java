package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalModelEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalModelService
 * @Date: 2025/9/12 23:34
 */
public interface IDigitalModelService {
    CursorPageResponseData<DigitalModelEntity> queryDigitalModelIndex(Long userId, Long lastCursorId, Integer size);
}
