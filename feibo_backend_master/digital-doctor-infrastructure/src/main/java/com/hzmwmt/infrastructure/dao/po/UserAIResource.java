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
 * @ClassName UserAIResource
 * @Date: 2025/8/26 14:12
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_user_ai_resource")
public class UserAIResource {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * 用户AI资源id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userAiResourceId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 资源对应的权益模板主键ID
     */
    private Long aiFeatureTemplateId;


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