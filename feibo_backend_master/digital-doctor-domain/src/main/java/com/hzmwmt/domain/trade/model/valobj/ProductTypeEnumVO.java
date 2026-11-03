package com.hzmwmt.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ProductTypeEnumVO
 * @Date: 2025/9/1 9:26
 */
@Getter
@AllArgsConstructor
public enum ProductTypeEnumVO {
    MEMBERSHIP_PLAN(false, "会员套餐"),
    RECHARGE_PACK(true, "充电包"),
    ;

    private final Boolean code;
    private final String desc;

    public static ProductTypeEnumVO getByCode(Boolean code) {
        // 增强健壮性，确保能够正确识别产品类型
        if (code == null) {
            return null;
        }
        // 根据Boolean值判断：false=会员套餐，true=充电包
        return code ? RECHARGE_PACK : MEMBERSHIP_PLAN;
    }

    /**
     * 从Integer值获取产品类型（兼容数据库中的0/1值）
     * 0 = 会员套餐，1 = 充电包
     */
    public static ProductTypeEnumVO getByIntegerCode(Integer code) {
        if (code == null) {
            return null;
        }
        return code == 0 ? MEMBERSHIP_PLAN : RECHARGE_PACK;
    }
}
