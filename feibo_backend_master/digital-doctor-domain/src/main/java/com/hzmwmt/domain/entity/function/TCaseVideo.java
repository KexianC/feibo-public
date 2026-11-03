package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_case_video")
@Schema(name = "TCaseVideo", description = "案例视频实体")
public class TCaseVideo {

    @Schema(description = "主键ID", example = "84")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "视频标题", example = "朱薛锋 丽水市人民医院")
    private String title;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "场景分类", example = "临床应用")
    private String scene;

    @Schema(description = "行业分类", example = "医疗健康")
    private String industry;

    @Schema(description = "封面图OSS链接", example = "https://xxx.oss-cn-beijing.aliyuncs.com/case-video/cover/xxx.png")
    private String coverUrl; // 原 indexImageUrl 改为 coverUrl（驼峰对应数据库 cover_url）

    @Schema(description = "视频文件OSS链接", example = "https://xxx.oss-cn-beijing.aliyuncs.com/case-video/file/xxx.mp4")
    private String videoUrl;

    @Schema(description = "创建时间", example = "2025-09-09T18:03:48")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "是否显示（1：是；0：否）", example = "1")
    private Integer isShow;

    @Schema(description = "逻辑删除标识（1：已删除；0：正常）", example = "0")
    @TableLogic
    private Integer isDeleted;
}
