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
 * @ClassName AIFeature
 * @Date: 2025/8/26 14:08
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_feature")
public class AIFeature {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * AI权益实例id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiFeatureId;

    /**
     * 关联的权益模板主键ID
     */
    private Long aiFeatureTemplateId;

    /**
     * 权益的额度值
     */
    private Integer value;

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
