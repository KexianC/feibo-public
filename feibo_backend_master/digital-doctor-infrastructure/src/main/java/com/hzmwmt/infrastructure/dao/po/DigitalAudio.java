package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalAudio
 * @Date: 2025/8/22 9:22
 */
@Builder
@Data
@TableName("t_digital_audio")
public class DigitalAudio {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 音频Id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalAudioId;

    /**
     * 用户ID，NULL表示系统资源
     */
    private Long userId;

    /**
     * 音频名称
     */
    private String name;

    /**
     * 音频URL
     */
    private String url;

    /**
     * 音频时长
     */
    private Integer duration;

    /**
     * 音频类型 true:用户录音，false:背景音乐
     */
    private Integer type;
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

