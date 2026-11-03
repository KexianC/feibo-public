package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.digital.adapter.port.IDigitalPort;
import com.hzmwmt.domain.digital.model.entity.MultipleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.SingleUploadEntity;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.infrastructure.gateway.digital.IDigitalApiService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalPort
 * @Date: 2025/8/25 16:22
 */
@Service
public class DigitalPort implements IDigitalPort {
    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private IDigitalApiService digitalApiService;

    @Override
    public String uploadSingle(SingleUploadEntity entity) {
        String originalFilename = entity.getFile().getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String userId;
        if (entity.getUserId() == null) {
            userId = "sys";
        } else {
            userId = entity.getUserId().toString();
        }

        String name = entity.getFolderName() + userId + "/" + uuid + fileExtension;
        return fileStorageGateway.upload(UploadDTO.builder()
                .file(entity.getFile())
                .path(name)
                .build()
        );
    }

    @Override
    public List<String> uploadMultiple(MultipleUploadEntity dto) {
        List<MultipartFile> files = dto.getFiles();
        List<CompletableFuture<String>> uploadTasks = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    return uploadSingle(SingleUploadEntity.builder()
                            .file(file)
                            .folderName(dto.getFolderName())
                            .userId(dto.getUserId())
                            .build());
                }, threadPoolExecutor))
                .toList();
        try {
            CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0])).join();
            return uploadTasks.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } catch (Exception e) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "批量上传过程中发生错误：" + e.getMessage());
        }
    }


    @Override
    public void delete(String objectUrl) {
        fileStorageGateway.delete(objectUrl);
    }

    @Override
    public VideoCompositionSubmissionEntity createVideoComposition(String body) {
        return digitalApiService.createVideoComposition(body);
    }
}
