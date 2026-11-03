package com.hzmwmt.domain.gateway;

import com.hzmwmt.domain.dto.UploadDTO;

/**
 * 文件存储网关
 */
public interface FileStorageGateway {
    String upload(UploadDTO dto);

    void delete(String objectUrl);
}