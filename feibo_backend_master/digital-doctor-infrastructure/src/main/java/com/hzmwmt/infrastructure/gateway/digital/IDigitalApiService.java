package com.hzmwmt.infrastructure.gateway.digital;

import com.fasterxml.jackson.databind.JsonNode;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalApiService
 * @Date: 2025/9/24 17:49
 */
public interface IDigitalApiService {
    VideoCompositionSubmissionEntity createVideoComposition(String requestBodyJson);

    JsonNode queryVideoResult(String taskId);

    JsonNode auditionVoice(String text,
                           String speaker,
                           String audioType,
                           double speed,
                           double volume,
                           double pitch);
}
