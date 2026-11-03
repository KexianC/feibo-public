package com.hzmwmt.domain.digital.service.video;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalVideoEntity;
import com.hzmwmt.domain.digital.service.IDigitalVideoService;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVideoService
 * @Date: 2025/8/22 10:07
 */
@Service
public class DigitalVideoService implements IDigitalVideoService {

    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalVideoEntity> queryUserVideoIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryUserVideoIndex(userId, lastCursorId, size);
    }

    @Override
    public CursorPageResponseData<DigitalVideoEntity> querySysVideoIndex(Long lastCursorId, Integer size) {
        return digitalRepository.querySysVideoIndex(lastCursorId, size);
    }
}
