package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频实体类（对应表：t_video）
 */
@Data
@TableName("t_video")
@Schema(name = "Video", description = "视频信息实体")
public class TVideo {

    @Schema(description = "主键ID", example = "22")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "视频标题", example = "测试视频22")
    private String title;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "封面图OSS链接", example = "https://xxx.oss-cn-beijing.aliyuncs.com/video/cover/xxx.jpg")
    private String coverUrl;

    @Schema(description = "视频文件OSS链接", example = "https://xxx.oss-cn-beijing.aliyuncs.com/video/file/xxx.mp4")
    private String videoUrl;

    @Schema(description = "视频时长", example = "00:00:00 或 00:00")
    private String duration;

    @Schema(description = "是否为预览视频（1：是；0：否）", example = "0")
    @TableField(exist = false) // 标记此字段在数据库中不存在，避免MyBatis-Plus自动生成SQL时包含它
    private Integer isPreview = 0;

    @Schema(description = "创建时间", example = "2023-08-01T17:18:24")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "是否显示（1：是；0：否）", example = "1")
    private Integer isShow;

    @Schema(description = "逻辑删除（1：已删除；0：正常）", example = "0")
    @TableLogic
    private Integer isDeleted;
}