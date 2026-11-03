package com.hzmwmt.domain.digital.service;

import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalUpdateService
 * @Date: 2025/8/25 15:23
 */
public interface IDigitalFileService {
    String uploadSingleFile(SingleUploadEntity entity);

    List<String> uploadMultipleFiles(MultipleUploadEntity entity);

    void deleteFile(String objectUrl);
}
