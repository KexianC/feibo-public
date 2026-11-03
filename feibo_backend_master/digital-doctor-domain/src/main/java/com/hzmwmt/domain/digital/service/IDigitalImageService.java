package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalImageEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IImageService
 * @Date: 2025/8/21 17:44
 */
public interface IDigitalImageService {

    CursorPageResponseData<DigitalImageEntity> queryUserBackgroundImageIndex(Long userId, Long lastCursorId, Integer size);


    CursorPageResponseData<DigitalImageEntity> queryUserIllustrationImageIndex(Long userId, Long lastCursorId, Integer size);


    CursorPageResponseData<DigitalImageEntity> querySysBackgroundImageIndex(Long lastCursorId, Integer size);


    CursorPageResponseData<DigitalImageEntity> querySysIllustrationImageIndex(Long lastCursorId, Integer size);
}
