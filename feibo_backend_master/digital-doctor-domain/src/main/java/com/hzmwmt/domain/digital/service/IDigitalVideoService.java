package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalVideoEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalVideoService
 * @Date: 2025/8/22 10:07
 */
public interface IDigitalVideoService {

    CursorPageResponseData<DigitalVideoEntity> queryUserVideoIndex(Long userId, Long lastCursorId, Integer size);


    CursorPageResponseData<DigitalVideoEntity> querySysVideoIndex(Long lastCursorId, Integer size);
}
