package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIPlanFeatureMap
 * @Date: 2025/8/27 13:13
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_plan_feature_map")
public class AIPlanFeatureMap {

    /**
     * 会员套餐主键ID
     */
    private Long aiMembershipPlanId;

    /**
     * 权益实例主键ID
     */
    private Long aiFeatureId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
