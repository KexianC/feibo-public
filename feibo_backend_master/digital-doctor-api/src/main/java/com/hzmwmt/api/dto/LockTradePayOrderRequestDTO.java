package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName LockTradePayOrderRequestDTO
 * @Date: 2025/8/29 14:12
 */
@Getter
public class LockTradePayOrderRequestDTO {
    @NotNull(message = "产品ID不能为空")
    private Long productId;
    /**
     * false:会员套餐 true:充电包
     */
    @NotNull(message = "产品类型不能为空")
    private Boolean productType;

}
