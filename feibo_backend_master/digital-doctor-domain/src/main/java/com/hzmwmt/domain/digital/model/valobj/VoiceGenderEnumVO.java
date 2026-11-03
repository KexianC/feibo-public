package com.hzmwmt.domain.digital.model.valobj;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VoiceGenderEnumVO
 * @Date: 2025/8/25 17:55
 */
@Getter
@AllArgsConstructor
public enum VoiceGenderEnumVO {
    male(false, "男"),
    female(true, "女"),
    ;

    private final Boolean code;
    private final String desc;

    public static VoiceGenderEnumVO getByCode(Boolean code) {
        if (code == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "性别参数错误");
        }
        if (code) {
            return female;
        }
        return male;
    }
}