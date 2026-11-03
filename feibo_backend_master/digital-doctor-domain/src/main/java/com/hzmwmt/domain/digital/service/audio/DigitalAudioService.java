package com.hzmwmt.domain.digital.service.audio;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalAudioEntity;
import com.hzmwmt.domain.digital.service.IDigitalAudioService;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalAudioService
 * @Date: 2025/8/22 10:08
 */
@Service
public class DigitalAudioService implements IDigitalAudioService {

    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalAudioEntity> queryUserMusicIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserMusicIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalAudioEntity> queryUserRecordingIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserRecordingIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalAudioEntity> querySysMusicIndex(Long lastCursorId, Integer size) {
        return digitalRepository.querySysMusicIndex(lastCursorId, size);
    }
}