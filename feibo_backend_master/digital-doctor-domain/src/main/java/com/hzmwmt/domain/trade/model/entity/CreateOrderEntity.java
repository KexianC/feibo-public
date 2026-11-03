package com.hzmwmt.domain.trade.model.entity;

import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CreateOrderEntity
 * @Date: 2025/9/1 0:13
 */
@Data
@Builder
public class CreateOrderEntity {
    private Long userId;
    private Long productId;
    private ProductTypeEnumVO productTypeEnumVO;
}
