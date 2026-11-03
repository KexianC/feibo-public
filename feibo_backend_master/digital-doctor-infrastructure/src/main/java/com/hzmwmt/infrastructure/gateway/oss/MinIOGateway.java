package com.hzmwmt.infrastructure.gateway.oss;

import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinIOGateway implements FileStorageGateway {
    @Value("${aliyun.oss.baseUrl}")
    String aliyunBaseUrl;
    @Resource
    private MinioClient minioClient;
    @Value("${minio.bucket-name}")
    private String bucketName;
    @Value("${minio.endpoint}")
    private String endpoint;

    @Override
    public String upload(UploadDTO dto) {
        MultipartFile file = dto.getFile();
        String objectName = dto.getPath();
        log.info("开始上传文件到MinIO");

        try (InputStream inputStream = file.getInputStream()) { // 自动关闭输入流
            // 1. 设置文件元数据（可选，但建议设置）
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 2. 打印上传成功日志（可选，用于确认）
            log.info("文件上传MinIO成功");

        } catch (Exception e) {
            // 3. 捕获上传异常并抛出
            log.error("文件上传至MinIO失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件上传至MinIO时发生错误: " + e.getMessage());
        }

        // 4. 返回MinIO文件的访问URL
        return endpoint + "/" + bucketName + "/" + objectName;
    }

    @Override
    public void delete(String objectUrl) {
        try {
            URL url = new URL(objectUrl);
            String objectKey = url.getPath().substring(1);
            // 检查对象是否存在
            minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            // 删除对象
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "从MinIO删除文件失败: url=" + objectUrl);
        }
    }

    /**
     * 生成预签名URL用于访问MinIO文件
     *
     * @param objectName 对象名称
     * @param expires    过期时间（秒）
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectName, int expires) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expires, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成预签名URL失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "生成预签名URL时发生错误: " + e.getMessage());
        }
    }

    /**
     * 从文件URL中提取对象名称
     *
     * @param objectUrl 文件URL
     * @return 对象名称
     */
    public String getObjectNameFromUrl(String objectUrl) {
        try {
            URL url = new URL(objectUrl);
            return url.getPath().substring(1); // 去掉开头的/
        } catch (Exception e) {
            log.error("从URL提取对象名称失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "从URL提取对象名称时发生错误: " + e.getMessage());
        }
    }

    /**
     * 将 minIO 的 url 转换为阿里云的 url
     *
     * @param minIOUrl minIO 的 url
     * @return 阿里云的 url
     */
    public String minIO2aliyun(String minIOUrl) {

        try {
            String objectName = getObjectNameFromUrl(minIOUrl);
            return aliyunBaseUrl + objectName;
        } catch (Exception e) {
            log.error("将 minIO 的 url 转换为阿里云的 url 失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "将 minIO 的 url 转换为阿里云的 url 时发生错误: " + e.getMessage());
        }

    }
}
