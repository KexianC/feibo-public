package com.hzmwmt.domain.digital.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalWorkEntity
 * @Date: 2025/9/24 13:44
 */
@Builder
@AllArgsConstructor
@Data
public class DigitalWorkEntity {
    /**
     * 业务id
     */
    private Long digitalWorkId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 请求头内容
     */
    private String body;

    /**
     * 模板标题
     */
    private String title;

    /**
     * 第三方平台返回id
     */
    private String externalId;
    private String quotaBizKey;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 视频URL地址
     */
    private String url;

    /**
     * 视频时长（单位：秒）
     */
    private Integer duration;

    /**
     * 生成数字作品的花费金额
     */
    private Double bill;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 是否为预览视频
     */
    private Boolean isPreview;

    /**
     * 状态字段：1表示成功，0表示失败
     */
    private Integer status;

    /** User-facing reason supplied for a failed composition. */
    private String failureReason;

    /** Business JSON actually sent to the provider, without auth headers. */
    private String composeParams;

    /** Client quota estimate in minutes. */
    private Integer videoTime;

    /** SHA-256 of composeParams. */
    private String requestSha256;

    /** Audit capture origin, currently provider_request for new submissions. */
    private String captureSource;

}
