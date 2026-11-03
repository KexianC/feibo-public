package com.hzmwmt.domain.copywriting.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: 审批操作类型枚举值对象
 * @ClassName OperationTypeEnumVO
 * @Date: 2025/1/20
 */
@Getter
@AllArgsConstructor
public enum OperationTypeEnumVO {

    SUBMIT(0, "审批通过"),
    CLAIM(1, "驳回");

    private final Integer code;
    private final String desc;

    public static OperationTypeEnumVO getByCode(Integer code) {
        for (OperationTypeEnumVO operation : OperationTypeEnumVO.values()) {
            if (operation.code.equals(code)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unknown operation type code: " + code);
    }
}