package com.hzmwmt.domain.digital.service.File;

import com.hzmwmt.domain.digital.adapter.port.IDigitalPort;
import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import com.hzmwmt.domain.digital.service.File.strategy.IUploadStrategy;
import com.hzmwmt.domain.digital.service.IDigitalFileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalFileService
 * @Date: 2025/8/25 15:28
 */
@Service
public class DigitalFileService implements IDigitalFileService {
    @Resource
    private UploadStrategyFactory strategyFactory;
    @Resource
    private IDigitalPort digitalPort;

    @Override
    public String uploadSingleFile(SingleUploadEntity entity) {
        IUploadStrategy strategy = strategyFactory.getStrategy(entity.getUploadType());
        strategy.prepareSingleFile(entity);
        entity.setFolderName(strategy.getFolderName());
        String uploadSingle = digitalPort.uploadSingle(entity);
        entity.setObjectUrl(uploadSingle);
        strategy.saveSingleFile(entity);
        return uploadSingle;
    }

    @Override
    public List<String> uploadMultipleFiles(MultipleUploadEntity entity) {
        IUploadStrategy strategy = strategyFactory.getStrategy(entity.getUploadType());
        strategy.prepareMultipleFiles(entity);
        entity.setFolderName(strategy.getFolderName());
        List<String> strings = digitalPort.uploadMultiple(entity);
        entity.setObjectUrlList(strings);
        strategy.saveMultipleFile(entity);
        return strings;
    }

    @Override
    public void deleteFile(String objectUrl) {
        digitalPort.delete(objectUrl);
    }
}
