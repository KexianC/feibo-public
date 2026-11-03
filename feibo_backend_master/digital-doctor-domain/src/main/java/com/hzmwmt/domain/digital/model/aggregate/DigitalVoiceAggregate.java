package com.hzmwmt.domain.digital.model.aggregate;

import com.hzmwmt.domain.digital.model.entity.DigitalVoiceEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceAggregate
 * @Date: 2025/8/25 23:21
 */
@Data
@Builder
public class DigitalVoiceAggregate {
    private DigitalVoiceEntity voiceEntity;
    private Boolean isCollected;
}
