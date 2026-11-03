package com.hzmwmt.domain.copywriting.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: 文案审批状态枚举值对象
 * @ClassName ApprovalStatusEnumVO
 * @Date: 2025/1/20
 */
@Getter
@AllArgsConstructor
public enum ApprovalStatusEnumVO {

    DOCTOR_PENDING(0, "作者待确认"),
    EXPERT_PENDING(1, "专家组待专业审核"),
    AI_PENDING(2, "AI自动审核中"),
    PLATFORM_PENDING(3, "所属医院/机构待终审"),
    COMPLETE(4, "审批完成"),
    FAILURE(5, "审批驳回");

    private final Integer code;
    private final String desc;

    public static ApprovalStatusEnumVO getByCode(Integer code) {
        for (ApprovalStatusEnumVO status : ApprovalStatusEnumVO.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown approval status code: " + code);
    }
}
