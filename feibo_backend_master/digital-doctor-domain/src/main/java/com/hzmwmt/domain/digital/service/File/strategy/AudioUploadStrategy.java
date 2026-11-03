package com.hzmwmt.domain.digital.service.File.strategy;

import com.hzmwmt.domain.digital.model.entity.DigitalAudioEntity;
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
 * @ClassName AudioUploadStrategy
 * @Date: 2025/8/22 11:48
 */
@Service
public class AudioUploadStrategy extends AbstractUpdateStrategy {

    @Override
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上传的音乐文件不能为空");
        }
        // 先检查MIME类型
        boolean isMimeTypeValid = Constants.OssConstants.UPLOAD_AUDIO_ALLOWED_CONTENT_TYPES.contains(file.getContentType());
        // 再检查文件扩展名，不区分大小写
        String originalFilename = file.getOriginalFilename();
        boolean isExtensionValid = originalFilename != null && originalFilename.matches(".*\\.(?i:mp3|wav|flac|aac|m4a)$");
        // 只要其中一个有效就允许上传
        if (!isMimeTypeValid && !isExtensionValid) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的音乐格式，仅支持 " + Constants.OssConstants.UPLOAD_AUDIO_TYPE_STRING);
        }
        if (file.getSize() > Constants.OssConstants.UPLOAD_AUDIO_MAX_SIZE) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "音乐大小不能超过 " + Constants.OssConstants.UPLOAD_AUDIO_MAX_SIZE_STRING);
        }
    }


    @Override
    public String getFolderName() {
        return Constants.OssConstants.UPLOAD_AUDIO_FOLDER;
    }

    @Override
    public void prepareSingleFile(SingleUploadEntity entity) {
        validate(entity.getFile());
        entity.setAudioDuration(FileUploadUtil.getAudioDurationWithTika(entity.getFile()));
    }

    @Override
    public void prepareMultipleFiles(MultipleUploadEntity entity) {
        validate(entity.getFiles());
        entity.setAudioDurations(FileUploadUtil.getAudioDurationsWithTika(entity.getFiles()));
    }

    @Override
    public void saveSingleFile(SingleUploadEntity singleUploadEntity) {
        Integer duration = requireDuration(singleUploadEntity.getAudioDuration());
        DigitalAudioEntity build = DigitalAudioEntity.builder()
                .url(singleUploadEntity.getObjectUrl())
                .type(singleUploadEntity.getUploadType().getCode())
                .userId(singleUploadEntity.getUserId())
                .duration(duration)
                .name(singleUploadEntity.getFile().getOriginalFilename())
                .build();
        digitalRepository.saveDigitalAudio(Collections.singletonList(build));
    }

    @Override
    public void saveMultipleFile(MultipleUploadEntity multipleUploadEntity) {
        List<MultipartFile> files = multipleUploadEntity.getFiles();
        List<String> urlList = multipleUploadEntity.getObjectUrlList();
        Long userId = multipleUploadEntity.getUserId();
        List<Integer> durations = multipleUploadEntity.getAudioDurations();
        if (durations == null || durations.size() != files.size() || urlList == null || urlList.size() != files.size()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "音频时长识别未完成，请重新上传");
        }
        List<DigitalAudioEntity> toSave = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String url = urlList.get(i);
            int duration = requireDuration(durations.get(i));
            DigitalAudioEntity build = DigitalAudioEntity.builder()
                    .name(file.getOriginalFilename())
                    .url(url)
                    .duration(duration)
                    .type(multipleUploadEntity.getUploadType().getCode())
                    .userId(userId)
                    .build();
            toSave.add(build);
        }
        digitalRepository.saveDigitalAudio(toSave);
    }

    private static int requireDuration(Integer duration) {
        if (duration == null || duration <= 0 || duration > 86400) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "音频时长识别失败，请检查文件或重新导出后上传");
        }
        return duration;
    }
}
