package com.hzmwmt.domain.user.model.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用于通过excel批量导入用户信息时的字段
 */
@Data
public class UserExcelModel {
    @ExcelProperty("姓名")
    private String nickName;

    @ExcelProperty("手机号")
    private String phoneNumber;

    @ExcelProperty("绑定模型（模型名称）")
    private String models;
}
