package com.hzmwmt.domain.service.file;

import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件服务类，封装文件上传到OSS的逻辑
 */
@Service // 标记为Spring服务，可被其他组件注入使用
public class FileService {

    // 注入OSSGateway，复用其上传逻辑
    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;
    ;

    /**
     * 通用OSS上传接口（支持图片类型，路径自动生成）
     *
     * @param file 前端上传的文件
     * @return 上传成功后的OSS文件访问URL
     */
    public String uploadToOss(MultipartFile file, String path) {
        // 1. 文件校验（确保文件非空且格式合法）
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        // 处理文件名可能为null的情况，以及格式校验
        if (originalFilename == null || !originalFilename.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
            throw new RuntimeException("仅支持jpg、jpeg、png、gif、webp格式");
        }

        // 2. 生成OSS存储路径（按业务规则自动生成，避免前端传递）
        // 路径格式：background-image/yyyyMMdd/UUID.扩展名（例如：background-image/20251025/123e4567-e89b-12d3-a456-426614174000.jpg）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 日期目录（如20251025）
        String uuid = UUID.randomUUID().toString().replaceAll("-", ""); // 生成无横线的UUID，确保文件名唯一
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf(".")); // 提取文件扩展名（如.jpg）
        String objectName = path + dateDir + "/" + uuid + fileExt; // 完整OSS路径

        // 3. 构造UploadDTO，封装文件和路径
        UploadDTO uploadDTO = new UploadDTO();
        uploadDTO.setFile(file); // 设置待上传的文件
        uploadDTO.setPath(objectName); // 设置生成的OSS路径

        // 4. 调用OSSGateway完成上传，返回文件访问URL
        return fileStorageGateway.upload(uploadDTO);
    }
}