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
 * @ClassName DigitalTemplate
 * @Date: 2025/9/24 20:48
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_digital_template")
public class DigitalTemplate {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 业务模板ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long digitalTemplateId;

    /**
     * 用户ID NULL说明是系统的模板
     */
    private Long userId;

    /**
     * 模板标题
     */
    private String title;

    private String imageId;
    /**
     * 模板内容
     */
    private String body;

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
