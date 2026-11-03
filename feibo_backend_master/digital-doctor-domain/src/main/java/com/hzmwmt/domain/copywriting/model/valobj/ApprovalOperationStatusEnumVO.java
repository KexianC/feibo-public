package com.hzmwmt.domain.copywriting.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalOperationStatusEnumVO
 * @Date: 2025/9/25 17:04
 */
@Getter
@AllArgsConstructor
public enum ApprovalOperationStatusEnumVO {

    APPROVE(true, "批准"),
    REJECT(false, "驳回");

    private final Boolean code;
    private final String desc;

    /**
     * 根据布尔值获取对应的枚举实例
     *
     * @param status 布尔状态值
     * @return 对应的枚举实例
     */
    public static ApprovalOperationStatusEnumVO getByStatus(Boolean status) {
        return status ? APPROVE : REJECT;
    }
}
