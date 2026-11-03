package com.hzmwmt.domain.ai.model.valobj;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIChatRoleEnumVO
 * @Date: 2025/9/11 10:49
 */
@Getter
@AllArgsConstructor
public enum AIChatRoleEnumVO {
    USER(false, "用户"),
    ASSISTANT(true, "AI助手");

    private final Boolean code;
    private final String desc;

    public static AIChatRoleEnumVO getByCode(Boolean code) {
        if (code == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "AI角色参数错误");
        }
        if (code) {
            return ASSISTANT;
        }
        return USER;
    }

}
