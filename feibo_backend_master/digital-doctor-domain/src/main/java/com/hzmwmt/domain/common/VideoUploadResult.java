package com.hzmwmt.domain.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "VideoUploadResult", description = "视频上传结果（含URL和时长）")
public class VideoUploadResult {
    @Schema(description = "视频OSS链接", example = "https://xxx.oss-cn-beijing.aliyuncs.com/video/file/xxx.mp4")
    private String videoUrl;

    @Schema(description = "视频时长（秒）", example = "150")
    private Integer durationSeconds;
}
