package com.hzmwmt.domain.digital.service.voice;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.aggregate.DigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.entity.DigitalVoiceCategoryEntity;
import com.hzmwmt.domain.digital.model.entity.DigitalVoiceEntity;
import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import com.hzmwmt.domain.digital.service.IDigitalVoiceService;
import com.hzmwmt.types.common.CursorPageResponseData;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CollectService
 * @Date: 2025/8/18 0:35
 */
@Service
public class DigitalVoiceService implements IDigitalVoiceService {
    @Resource
    private IDigitalRepository digitalRepository;

    @Override
    public CursorPageResponseData<DigitalVoiceAggregate> queryVoiceIndex(Long lastCursorId, Integer size, Long userId) {
        return digitalRepository.queryVoiceIndex(lastCursorId, size, userId);
    }

    @Override
    public CursorPageResponseData<DigitalVoiceAggregate> advancedVoiceQuery(
            Long lastCursorId,
            Integer size,
            VoiceGenderEnumVO voiceGenderEnumVO,
            String name,
            Long userId,
            List<Long> digitalVoiceCategoryIdList
    ) {

        // 直接透传给 Mapper 或远程客户端，不再聚合
        return digitalRepository.advancedVoiceQuery(
                lastCursorId,
                size,
                voiceGenderEnumVO,
                name,
                userId,
                digitalVoiceCategoryIdList
        );
    }

    @Override
    public CursorPageResponseData<DigitalVoiceEntity> queryVoiceCollectedIndex(Long lastCursorId, Integer size, Long userId) {
        return digitalRepository.queryVoiceCollectedIndex(lastCursorId, size, userId);
    }


    @Override
    public void updateVoiceFavoriteStatus(Long userId, Long voiceId, boolean collected) {
        digitalRepository.updateVoiceFavoriteStatus(userId, voiceId, collected);
    }

    @Override
    public List<DigitalVoiceCategoryEntity> queryDigitalVoiceCategory(DigitalVoiceCategoryTypeEnumVO digitalVoiceCategoryTypeEnumVO) {
        return digitalRepository.queryDigitalVoiceCategory(digitalVoiceCategoryTypeEnumVO);
    }

}
