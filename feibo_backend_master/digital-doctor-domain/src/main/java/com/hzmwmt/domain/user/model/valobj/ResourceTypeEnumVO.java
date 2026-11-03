package com.hzmwmt.domain.user.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MediaResourceTypeEnumVO
 * @Date: 2025/9/8 10:42
 */
@Getter
@AllArgsConstructor
public enum ResourceTypeEnumVO {
    AI_MEMBERSHIP_PLAN(false, "AI会员套餐"),
    AI_RECHARGE_PACK(true, "AI充电包");
    private final Boolean code;
    private final String description;
}
