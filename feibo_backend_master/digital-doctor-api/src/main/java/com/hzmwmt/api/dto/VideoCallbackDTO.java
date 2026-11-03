package com.hzmwmt.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 视频生成回调数据传输对象
 * 用于接收视频合成服务完成后的回调数据
 * 数据结构与视频合成结果查询一致
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoCallbackDTO implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 视频高度
     */
    private Integer height;

    /**
     * 视频宽度
     */
    private Integer width;

    /**
     * 封面图片URL
     */
    private String coverUrl;

    /**
     * 视频结果URL
     */
    private String resultUrl;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 状态
     * suc: 成功
     * 其他: 失败
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 视频时长（毫秒）
     */
    private Long duration;

    /**
     * 创建时间
     */
    private String created;

    /**
     * 最后更新时间
     */
    private String lastUpdateTime;
}