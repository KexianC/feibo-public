package com.hzmwmt.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件DTO，用于传递文件和存储路径
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadDTO {
    private MultipartFile file; // 待上传的文件
    private String path;       // 文件在OSS中的存储路径（如：image/20251025/xxx.jpg）
}
