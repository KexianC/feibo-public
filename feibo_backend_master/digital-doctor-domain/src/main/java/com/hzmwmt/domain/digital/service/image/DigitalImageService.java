package com.hzmwmt.domain.digital.service.image;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalImageEntity;
import com.hzmwmt.domain.digital.service.IDigitalImageService;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalImageService
 * @Date: 2025/8/22 10:06
 */
@Service
public class DigitalImageService implements IDigitalImageService {

    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalImageEntity> queryUserBackgroundImageIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserBackgroundImageIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> queryUserIllustrationImageIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserIllustrationImageIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> querySysBackgroundImageIndex(Long lastCursorId, Integer size) {
        return digitalRepository.querySysBackgroundImageIndex(lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> querySysIllustrationImageIndex(Long lastCursorId, Integer size) {
        return digitalRepository.querySysIllustrationImageIndex(lastCursorId, size);
    }
}
