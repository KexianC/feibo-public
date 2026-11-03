package com.hzmwmt.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ResourceUserTypeEnumVO
 * @Date: 2025/9/20 15:14
 */
@Getter
@AllArgsConstructor
public enum ResourceUserTypeEnumVO {


    DOCTOR(false, "医生"),


    DOCTOR_ASSISTANT(true, "医生助手");

    /**
     * 类型的唯一代码，用于系统内部识别
     */
    private final Boolean code;

    /**
     * 描述
     */
    private final String desc;


    /**
     * 根据 code 获取对应的枚举实例
     *
     * @param code 用户类型代码
     * @return ResourceUserTypeEnumVO 枚举实例
     */
    public static ResourceUserTypeEnumVO getByCode(Boolean code) {
        if (code) {
            return DOCTOR_ASSISTANT;
        }
        return DOCTOR;
    }
}