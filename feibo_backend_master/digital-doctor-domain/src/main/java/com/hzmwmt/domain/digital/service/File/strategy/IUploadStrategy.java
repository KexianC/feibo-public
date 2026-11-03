package com.hzmwmt.domain.digital.service.File.strategy;

import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UploadStrategy
 * @Date: 2025/8/22 11:32
 */
public interface IUploadStrategy {
    /**
     * 校验文件
     */
    void validate(MultipartFile file);

    void validate(List<MultipartFile> files);

    /** Complete validation and server-side metadata probing before uploading objects. */
    default void prepareSingleFile(SingleUploadEntity entity) {
        validate(entity.getFile());
    }

    default void prepareMultipleFiles(MultipleUploadEntity entity) {
        validate(entity.getFiles());
    }

    /**
     * 获取存储文件夹名称
     */
    String getFolderName();

    void saveSingleFile(SingleUploadEntity singleUploadEntity);

    void saveMultipleFile(MultipleUploadEntity multipleUploadEntity);

}
