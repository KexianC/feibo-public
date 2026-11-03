package com.hzmwmt.domain.digital.model.valobj;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryTypeEnumVO
 * @Date: 2025/9/25 13:07
 */
@Getter
@AllArgsConstructor
public enum DigitalVoiceCategoryTypeEnumVO {

    AGE(0, "年龄"),
    DOMAIN(1, "领域"),
    STYLE(2, "风格"),
    LANGUAGE(3, "语言");

    private final int code;
    private final String desc;

    public static DigitalVoiceCategoryTypeEnumVO getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Stream.of(values())
                .filter(item -> item.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无效的分类类型码"));
    }
}
