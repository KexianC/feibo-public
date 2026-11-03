package com.hzmwmt.domain.digital.service.File.strategy;

import com.hzmwmt.domain.digital.model.entity.DigitalImageEntity;
import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ImageUploadStrategy
 * @Date: 2025/8/22 11:34
 */
@Service
public class ImageUploadStrategy extends AbstractUpdateStrategy {

    @Override
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上传的图片文件不能为空");
        }
        if (!Constants.OssConstants.UPLOAD_IMAGE_ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的图片格式，仅支持 " + Constants.OssConstants.UPLOAD_IMAGE_TYPE_STRING);
        }
        if (file.getSize() > Constants.OssConstants.UPLOAD_IMAGE_MAX_SIZE) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "图片大小不能超过 " + Constants.OssConstants.UPLOAD_IMAGE_MAX_SIZE_STRING);
        }
    }

    @Override
    public String getFolderName() {
        return Constants.OssConstants.UPLOAD_IMAGE_FOLDER;
    }

    @Override
    public void saveSingleFile(SingleUploadEntity singleUploadEntity) {
        DigitalImageEntity imageEntity = DigitalImageEntity.builder()
                .userId(singleUploadEntity.getUserId())
                .name(singleUploadEntity.getFile().getOriginalFilename())
                .url(singleUploadEntity.getObjectUrl())
                .type(singleUploadEntity.getUploadType().equals(UploadStrategyFactory.UploadTypeEnumVO.ILLUSTRATION))
                .build();
        digitalRepository.saveDigitalImage(Collections.singletonList(imageEntity));
    }

    @Override
    public void saveMultipleFile(MultipleUploadEntity multipleUploadEntity) {
        List<MultipartFile> files = multipleUploadEntity.getFiles();
        List<String> urlList = multipleUploadEntity.getObjectUrlList();
        Long userId = multipleUploadEntity.getUserId();
        boolean isIllustration = multipleUploadEntity.getUploadType().equals(UploadStrategyFactory.UploadTypeEnumVO.ILLUSTRATION);
        List<DigitalImageEntity> toSave = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String url = urlList.get(i);
            DigitalImageEntity build = DigitalImageEntity.builder()
                    .name(file.getOriginalFilename())
                    .url(url)
                    .type(isIllustration)
                    .userId(userId)
                    .build();
            toSave.add(build);
        }
        digitalRepository.saveDigitalImage(toSave);

    }
}