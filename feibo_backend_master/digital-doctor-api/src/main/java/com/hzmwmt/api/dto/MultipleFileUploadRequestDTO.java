package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MultipleFileUploadRequestDTO
 * @Date: 2025/9/13 14:51
 */
@Data
public class MultipleFileUploadRequestDTO {

    private Long userId;
    @NotNull(message = "文件列表不能为空")
    private List<MultipartFile> files;

    @NotNull(message = "上传类型不能为空")
    private Integer uploadType;
}
