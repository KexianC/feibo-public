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
 * @ClassName UserAIResourceDetail
 * @Date: 2025/9/20 19:13
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_user_ai_resource_detail")
public class UserAIResourceDetail {

    /**
     * 主键ID，数据库自增
     */

    private Long id;

    /**
     * 用户AI资源详情表主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userAiResourceDetailId;


    /**
     * 关联到t_user_ai_resource表的主键ID
     */
    private Long userAiResourceId;

    /**
     * 当前批次的剩余额度
     */
    private Integer balance;

    /**
     * 资源类型: 0=订阅权益, 1=购买权益
     */
    private Boolean type;

    /**
     * 过期时间
     */
    private Date expiryTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最后更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

}