package com.hzmwmt.domain.digital.adapter.port;

import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalPort
 * @Date: 2025/8/25 15:48
 */
public interface IDigitalPort {
    String uploadSingle(SingleUploadEntity entity);

    List<String> uploadMultiple(MultipleUploadEntity dto);

    void delete(String objectUrl);

    VideoCompositionSubmissionEntity createVideoComposition(String body);
}