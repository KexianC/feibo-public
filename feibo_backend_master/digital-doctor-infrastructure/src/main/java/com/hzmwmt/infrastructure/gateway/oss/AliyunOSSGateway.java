package com.hzmwmt.infrastructure.gateway.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;

/**
 * 阿里云oss文件存储网关，封装OSS上传、删除等操作
 */
@Slf4j
@Component
public class AliyunOSSGateway implements FileStorageGateway {

    @Resource
    private OSS ossClient; // 注入OSS客户端（由OSSConfig创建）

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName; // 存储桶名称（从配置文件读取）

    @Value("${aliyun.oss.endpoint}")
    private String endpoint; // OSS地域节点（从配置文件读取）


    /**
     * 上传文件到OSS
     *
     * @param dto 包含待上传文件和存储路径的DTO
     * @return 上传成功后的文件访问URL
     */
    public String upload(UploadDTO dto) {
        MultipartFile file = dto.getFile();
        String objectName = dto.getPath(); // OSS中的文件路径（objectKey）

        // 校验文件和路径
        if (file == null || file.isEmpty()) {
            log.error("上传失败：文件为空");
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件不能为空");
        }
        if (objectName == null || objectName.trim().isEmpty()) {
            log.error("上传失败：存储路径为空");
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "存储路径不能为空");
        }

        log.info("开始上传文件到OSS");

        try (InputStream inputStream = file.getInputStream()) { // 自动关闭输入流
            // 设置文件元数据（可选，增强文件管理）
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize()); // 文件大小（字节）
            metadata.setContentType(file.getContentType()); // MIME类型（如image/jpeg、audio/mpeg）

            // 核心：调用OSS SDK上传文件
            PutObjectResult result = ossClient.putObject(
                    bucketName,    // 存储桶名称
                    objectName,    // OSS中的文件路径
                    inputStream,   // 文件输入流
                    metadata       // 元数据
            );

            log.info("文件上传OSS成功");

            // 生成文件访问URL（HTTPS）
            return "https://" + bucketName + "." + endpoint + "/" + objectName;

        } catch (Exception e) {
            // 捕获所有异常（网络错误、权限不足等）
            log.error("文件上传OSS失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(),
                    "上传失败：" + e.getMessage());
        }
    }


    /**
     * 从OSS删除文件（通过文件URL）
     *
     * @param fileUrl OSS文件访问URL（如：https://xxx.oss-cn-hangzhou.aliyuncs.com/image/xxx.jpg）
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            log.error("删除失败：文件URL为空");
            throw new AppException(ResponseCode.DELETE_EXCEPTION.getCode(), "文件URL不能为空");
        }

        try {
            // 从URL中解析出OSS的objectKey（文件路径）
            URL url = new URL(fileUrl);
            String objectKey = url.getPath().substring(1); // 去掉路径中的第一个"/"

            // 检查文件是否存在
            if (ossClient.doesObjectExist(bucketName, objectKey)) {
                // 执行删除
                ossClient.deleteObject(bucketName, objectKey);
                log.info("文件从OSS删除成功");
            } else {
                log.warn("文件不存在，无需删除");
            }

        } catch (Exception e) {
            log.error("文件从OSS删除失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.DELETE_EXCEPTION.getCode(),
                    "删除失败：" + e.getMessage());
        }
    }

}
