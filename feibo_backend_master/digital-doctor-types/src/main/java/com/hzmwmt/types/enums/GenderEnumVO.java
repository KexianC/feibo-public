package com.hzmwmt.types.enums;

import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName GenderEnumVO
 * @Date: 2025/9/4 14:44
 */
@Getter
@AllArgsConstructor
public enum GenderEnumVO {

    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final int code;
    private final String description;

    public static GenderEnumVO fromCode(int code) {
        for (GenderEnumVO gender : GenderEnumVO.values()) {
            if (gender.getCode() == code) {
                return gender;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "性别参数异常");
    }

    public static GenderEnumVO fromDescription(String description) {
        for (GenderEnumVO gender : GenderEnumVO.values()) {
            if (Objects.equals(gender.getDescription(), description)) {
                return gender;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "性别参数异常");
    }
}
