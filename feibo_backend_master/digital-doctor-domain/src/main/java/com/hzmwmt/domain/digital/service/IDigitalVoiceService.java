package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.aggregate.DigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.entity.DigitalVoiceCategoryEntity;
import com.hzmwmt.domain.digital.model.entity.DigitalVoiceEntity;
import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import com.hzmwmt.types.common.CursorPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICollectService
 * @Date: 2025/8/18 0:14
 */
public interface IDigitalVoiceService {
    CursorPageResponseData<DigitalVoiceAggregate> queryVoiceIndex(Long lastCursorId, Integer size, Long userId);

    CursorPageResponseData<DigitalVoiceAggregate> advancedVoiceQuery(
            Long lastCursorId,
            Integer size,
            VoiceGenderEnumVO voiceGenderEnumVO,
            String name,
            Long userId,
            List<Long> digitalVoiceCategoryIdList);

    CursorPageResponseData<DigitalVoiceEntity> queryVoiceCollectedIndex(Long lastCursorId, Integer size, Long userId);

    void updateVoiceFavoriteStatus(Long userId, Long voiceId, boolean collected);

    List<DigitalVoiceCategoryEntity> queryDigitalVoiceCategory(DigitalVoiceCategoryTypeEnumVO digitalVoiceCategoryTypeEnumVO);
}
