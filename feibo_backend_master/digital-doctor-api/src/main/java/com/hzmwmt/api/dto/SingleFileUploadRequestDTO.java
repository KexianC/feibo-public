package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SingleFileUploadRequestDTO
 * @Date: 2025/9/13 14:50
 */
@Data
public class SingleFileUploadRequestDTO {

    private Long userId;

    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    @NotNull(message = "上传类型不能为空")
    private Integer uploadType;
}
