package com.hzmwmt.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaymentStatusEnumVO
 * @Date: 2025/9/29 22:52
 */
@Getter
@AllArgsConstructor
public enum PaymentStatusEnumVO {

    SUCCESS("0000", "支付成功"),
    PROCESSING("2xxx", "处理中"),
    UNPAID("3088", "未支付"),
    FAILED("3xxx", "支付失败"),
    UNKNOWN("UNKNOWN", "未知状态");

    private final String code;
    private final String desc;

    /**
     * 根据状态码获取枚举
     */
    public static PaymentStatusEnumVO getByCode(String trxStatus) {
        if (trxStatus == null) {
            return UNKNOWN;
        }

        if ("0000".equals(trxStatus)) {
            return SUCCESS;
        }
        if ("3088".equals(trxStatus)) {
            return UNPAID;
        }
        if (trxStatus.startsWith("2")) {
            return PROCESSING;
        }
        if (trxStatus.startsWith("3")) {
            return FAILED;
        }

        return UNKNOWN;
    }
}
