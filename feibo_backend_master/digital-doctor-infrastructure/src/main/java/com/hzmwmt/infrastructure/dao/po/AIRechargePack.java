package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIRechargePack
 * @Date: 2025/8/26 14:10
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_recharge_pack")
public class AIRechargePack {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * 数字包id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiRechargePackId;

    /**
     * 数字包唯一编码
     */
    private String packCode;

    /**
     * 数字包名称
     */
    private String name;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 充值的目标权益模板ID, 数字包本身就是权益
     */
    private Long aiFeatureTemplateId;

    /**
     * 充值的额度
     */
    private Integer value;

    /**
     * 数字包详细描述
     */
    private String description;

    /**
     * 是否上架: 0=上架, 1=不上架, 默认0
     */
    private Boolean isActive;

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