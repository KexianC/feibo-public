package com.hzmwmt.domain.digital.model.entity;

import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SingleUploadEntity
 * @Date: 2025/8/25 15:53
 */
@Data
@Builder
public class SingleUploadEntity {
    private Long userId;
    private String folderName;
    private MultipartFile file;
    private String objectUrl;
    private UploadStrategyFactory.UploadTypeEnumVO uploadType;
    /** Server-probed before object upload; never supplied by the client. */
    private Integer audioDuration;


}
