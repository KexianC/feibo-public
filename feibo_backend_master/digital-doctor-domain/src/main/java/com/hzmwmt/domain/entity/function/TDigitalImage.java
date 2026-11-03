package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字图片实体类
 * 用于管理所有图片资源，包括背景图片(type=0)和插图(type=1)
 */
@Data
@TableName("t_digital_image")
public class TDigitalImage {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数字图片ID
     */
    @TableField("digital_image_id")
    private Long digitalImageId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 图片类型：0-背景图片，1-插图
     */
    private Integer type;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}