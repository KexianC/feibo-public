package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName LockTradePayOrderResponseDTO
 * @Date: 2025/9/3 11:13
 */
@Data
@Builder
public class LockTradePayOrderResponseDTO implements Serializable {
    private String url;
    private String trxId;
    private Long orderId;
}
