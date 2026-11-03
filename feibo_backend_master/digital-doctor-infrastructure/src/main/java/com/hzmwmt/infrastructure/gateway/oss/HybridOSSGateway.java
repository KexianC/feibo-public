package com.hzmwmt.infrastructure.gateway.oss;

import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 混合OSS网关
 */
@Component
@Slf4j
public class HybridOSSGateway implements FileStorageGateway {

    @Resource
    private MinIOGateway minIOGateway;

    @Resource
    private AliyunOSSGateway aliyunOSSGateway;

    /**
     * 双删除
     *
     * @param objectUrl minIO 的 url
     */
    @Override
    public void delete(String objectUrl) {
        minIOGateway.delete(objectUrl);
        String aliyunUrl = minIOGateway.minIO2aliyun(objectUrl);
        aliyunOSSGateway.delete(aliyunUrl);
    }

    /**
     * 双上传
     *
     * @param dto
     * @return
     */
    @Override
    public String upload(UploadDTO dto) {
        String minioUrl = minIOGateway.upload(dto);
        log.info("MinIO 上传成功");
        aliyunOSSGateway.upload(dto);
        log.info("阿里云 OSS 上传成功");
        return minioUrl;
    }
}
