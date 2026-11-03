package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalWorkEntity;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalWorkService
 * @Date: 2025/9/24 13:43
 */
public interface IDigitalWorkService {
    String createVideoComposition(VideoCompositionEntity videoCompositionEntity);

    void deleteDigitalWork(DigitalWorkEntity entity);

    void updateDigitalWorkTitle(DigitalWorkEntity entity);

    CursorPageResponseData<DigitalWorkEntity> queryDigitalWorkIndex(Long userId, Long lastCursorId, Integer size);
}
