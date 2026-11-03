package com.hzmwmt.domain.trade.model.valobj;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OrderStatusEnumVO
 * @Date: 2025/8/31 23:12
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnumVO {

    CREATE(0, "创建完成 - 如果调单了，也会从创建记录重新发起创建支付单"),
    PAY_WAIT(1, "等待支付 - 订单创建完成后，创建支付单"),
    PAY_SUCCESS(2, "支付成功 - 接收到支付回调消息"),
    DEAL_DONE(3, "交易完成 - 商品发货完成"),
    REFUND_WAIT(4, "等待退款"),
    CLOSE(5, "关单 - 支付失败或超时"),
    ;

    private final int code;
    private final String desc;

    public static OrderStatusEnumVO getByCode(int code) {
        // values() 方法会返回所有枚举实例的数组
        for (OrderStatusEnumVO status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        // 如果循环结束还没找到，说明传入的code是无效的
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无效的订单状态码 ");
    }

}