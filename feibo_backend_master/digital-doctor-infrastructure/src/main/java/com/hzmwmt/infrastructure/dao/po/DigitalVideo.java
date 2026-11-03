package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: 数字视频实体类
 * @ClassName DigitalVideo
 * @Date: 2025/8/22 9:21
 */
@Builder
@Data
@TableName("t_digital_video")
public class DigitalVideo {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 视频业务ID (雪花ID)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalVideoId;

    /**
     * 用户ID，NULL表示系统资源
     */
    private Long userId;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 视频URL
     */
    private String url;

    /**
     * 音频时长
     */
    private Integer duration;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
