package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalWork
 * @Date: 2025/9/24 13:40
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_work")
public class DigitalWork {

    /**
     * 主键
     */
    private Integer id;


    /**
     * 业务id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalWorkId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 请求头内容
     */
    private String body;

    /**
     * 第三方平台返回id
     */
    private String externalId;
    private String quotaBizKey;
    private Boolean quotaHidden;

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
     * Provider display name; the persisted work name is stored in title.
     */
    @TableField(exist = false)
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

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
