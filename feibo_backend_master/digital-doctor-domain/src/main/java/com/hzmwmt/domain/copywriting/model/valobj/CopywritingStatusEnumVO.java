package com.hzmwmt.domain.copywriting.model.valobj;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: 文案状态枚举值对象
 * @ClassName CopywritingStatusEnumVO
 * @Date: 2025/1/20
 */
@Getter
@AllArgsConstructor
public enum CopywritingStatusEnumVO {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    ARCHIVED(2, "发布成功"),
    Failure(3, "发布失败");

    private final Integer code;
    private final String desc;

    public static CopywritingStatusEnumVO getByCode(Integer code) {
        for (CopywritingStatusEnumVO status : CopywritingStatusEnumVO.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无效的文案状态");
    }
}