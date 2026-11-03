package com.hzmwmt.domain.digital.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalAudioEntity
 * @Date: 2025/8/22 9:40
 */
@Data
@Builder
public class DigitalAudioEntity {
    /**
     * 音频Id
     */
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
     * 音频类型：0音乐 1录音（数据库是int，实体类改为Integer匹配）
     */
    private Integer type;

    /**
     * 创建时间（实体类新增）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（实体类新增）
     */
    private LocalDateTime updateTime;
}
