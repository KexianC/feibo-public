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
 * @ClassName DigitalImage
 * @Date: 2025/8/22 9:15
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_digital_image")
public class DigitalImage {

    /**
     * 主键IDv
     */
    private Long id;

    /**
     * 图片业务ID (雪花ID)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalImageId;

    /**
     * 用户ID，NULL表示系统资源
     */
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
     * 图片类型: 0-背景, 1-插图
     */
    private Integer type;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}