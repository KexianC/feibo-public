package com.hzmwmt.domain.digital.service.File.strategy;

import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.types.common.Constants.OssConstants;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AbstractFileStrategy
 * @Date: 2025/8/25 16:12
 */
public abstract class AbstractUpdateStrategy implements IUploadStrategy {
    @Resource
    protected IDigitalRepository digitalRepository;

    @Override
    public void validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上传的文件不能为空");
        }
        if (files.size() > OssConstants.UPLOAD_BATCH_MAX_TOTAL_SIZE) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上传的文件不能超过" + OssConstants.UPLOAD_BATCH_MAX_TOTAL_SIZE + "个");
        }
        for (MultipartFile file : files) {
            validate(file);
        }
    }

}
