package com.hzmwmt.domain.trade.model.entity;

import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradePaySuccessEntity
 * @Date: 2025/8/29 9:22
 */
@Data
@Builder
public class TradePaySuccessEntity {
    private Long orderId;
    private Long userId;
    private Long productId;
    private ProductTypeEnumVO productTypeEnumVO;
}
