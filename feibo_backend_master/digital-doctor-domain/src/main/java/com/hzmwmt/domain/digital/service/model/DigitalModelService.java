package com.hzmwmt.domain.digital.service.model;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalModelEntity;
import com.hzmwmt.domain.digital.service.IDigitalModelService;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalModelService
 * @Date: 2025/9/12 23:53
 */
@Service
public class DigitalModelService implements IDigitalModelService {
    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalModelEntity> queryDigitalModelIndex(Long userId, Long lastCursorId, Integer size) {
        return digitalRepository.queryDigitalModelIndex(userId, lastCursorId, size);
    }
}
