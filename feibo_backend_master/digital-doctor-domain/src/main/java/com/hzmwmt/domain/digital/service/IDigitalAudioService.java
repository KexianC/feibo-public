package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.DigitalAudioEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalAudioService
 * @Date: 2025/8/22 10:07
 */
public interface IDigitalAudioService {

    CursorPageResponseData<DigitalAudioEntity> queryUserMusicIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalAudioEntity> queryUserRecordingIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalAudioEntity> querySysMusicIndex(Long lastCursorId, Integer size);
}