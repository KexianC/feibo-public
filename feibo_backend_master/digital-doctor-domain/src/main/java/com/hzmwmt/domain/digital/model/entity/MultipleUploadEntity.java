package com.hzmwmt.domain.digital.model.entity;

import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MultipleUploadEntity
 * @Date: 2025/8/25 15:56
 */
@Data
public class MultipleUploadEntity {
    private Long userId;
    private String folderName;
    private List<String> objectUrlList;
    private List<MultipartFile> files;
    private UploadStrategyFactory.UploadTypeEnumVO uploadType;
    /** Server-probed durations in the same order as files. */
    private List<Integer> audioDurations;

}
