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
 * @ClassName AIFeatureTemplateEntity
 * @Date: 2025/8/27 13:10
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_feature_template")
public class AIFeatureTemplate {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * AI权益模板id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiFeatureTemplateId;

    /**
     * 权益模板业务代码, 程序识别用
     */
    private String templateCode;

    /**
     * 权益模板名称
     */
    private String name;

    /**
     * 权益模板详细描述
     */
    private String description;

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