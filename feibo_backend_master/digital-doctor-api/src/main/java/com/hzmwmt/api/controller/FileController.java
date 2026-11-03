package com.hzmwmt.api.controller;

import com.hzmwmt.api.response.Response;
import com.hzmwmt.infrastructure.gateway.oss.MinIOGateway;
import com.hzmwmt.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 文件访问控制器
 * 用于生成预签名URL访问MinIO文件
 */
@Deprecated(forRemoval = true)
@Slf4j
public class FileController {
    @Resource
    private MinIOGateway ossGateway;

    /**
     * 获取文件预签名URL
     *
     * @param url     原始文件URL
     * @param expires 过期时间（秒，默认3600秒）
     * @return 预签名URL
     */
    // Intentionally not exposed as an HTTP endpoint. The former endpoint
    // accepted an arbitrary URL and could mint a signed URL for any object in
    // the configured bucket. Reintroduce only with a server-side resource ID
    // lookup scoped to the authenticated owner.
    public Response<String> getPresignedUrl(
            @RequestParam("url") String url,
            @RequestParam(value = "expires", defaultValue = "3600") int expires) {
        try {
            // 从URL中提取对象名称
            String objectName = ossGateway.getObjectNameFromUrl(url);
            // 生成预签名URL
            String presignedUrl = ossGateway.getPresignedUrl(objectName, expires);
            return Response.success(presignedUrl);
        } catch (Exception e) {
            log.error("生成文件预签名URL失败: type={}", e.getClass().getSimpleName());
            return Response.fail(ResponseCode.UPLOAD_EXCEPTION.getCode(), ResponseCode.UPLOAD_EXCEPTION.getInfo());
        }
    }
}

