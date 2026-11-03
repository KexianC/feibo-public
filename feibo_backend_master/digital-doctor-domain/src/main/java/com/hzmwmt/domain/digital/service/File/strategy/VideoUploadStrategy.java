package com.hzmwmt.domain.digital.service.File.strategy;

import com.hzmwmt.domain.digital.model.entity.DigitalVideoEntity;
import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.util.FileUploadUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VideoUploadStrategy
 * @Date: 2025/8/22 11:48
 */
@Service
public class VideoUploadStrategy extends AbstractUpdateStrategy {

    @Override
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上传的视频文件不能为空");
        }
        if (!Constants.OssConstants.UPLOAD_VIDEO_ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的视频格式，仅支持 " + Constants.OssConstants.UPLOAD_VIDEO_TYPE_STRING);
        }
        if (file.getSize() > Constants.OssConstants.UPLOAD_VIDEO_MAX_SIZE) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "视频大小不能超过 " + Constants.OssConstants.UPLOAD_VIDEO_MAX_SIZE_STRING);
        }
    }

    @Override
    public String getFolderName() {
        return Constants.OssConstants.UPLOAD_VIDEO_FOLDER;
    }

    @Override
    public void saveSingleFile(SingleUploadEntity singleUploadEntity) {
        int duration = FileUploadUtil.getAudioDurationWithTika(singleUploadEntity.getFile());
        DigitalVideoEntity build = DigitalVideoEntity.builder()
                .name(singleUploadEntity.getFile().getOriginalFilename())
                .url(singleUploadEntity.getObjectUrl())
                .duration(duration)
                .userId(singleUploadEntity.getUserId())
                .build();
        digitalRepository.saveDigitalVideo(Collections.singletonList(build));
    }

    @Override
    public void saveMultipleFile(MultipleUploadEntity multipleUploadEntity) {
        List<MultipartFile> files = multipleUploadEntity.getFiles();
        List<Integer> durations = FileUploadUtil.getAudioDurationsWithTika(files);
        List<String> urlList = multipleUploadEntity.getObjectUrlList();
        Long userId = multipleUploadEntity.getUserId();

        List<DigitalVideoEntity> toSave = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String url = urlList.get(i);
            int duration = durations.get(i);
            DigitalVideoEntity build = DigitalVideoEntity.builder()
                    .name(file.getOriginalFilename())
                    .url(url)
                    .duration(duration)
                    .userId(userId)
                    .build();
            toSave.add(build);
        }
        digitalRepository.saveDigitalVideo(toSave);
    }
}