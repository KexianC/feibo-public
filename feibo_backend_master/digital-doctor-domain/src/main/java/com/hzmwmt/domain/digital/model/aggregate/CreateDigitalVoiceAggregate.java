package com.hzmwmt.domain.digital.model.aggregate;

import com.hzmwmt.domain.digital.model.entity.DigitalVoiceEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CreateDigitalVoiceAggregate
 * @Date: 2025/8/25 23:28
 */
@Builder
@Data
public class CreateDigitalVoiceAggregate {
    private DigitalVoiceEntity videoEntity;
    private Long digitalVoiceAgeCategoryId;
    private Long digitalVoiceDomainCategoryId;
    private Long digitalVoiceLanguageCategoryId;
    private Long digitalVoiceStyleCategoryId;
}
