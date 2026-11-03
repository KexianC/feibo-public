package com.hzmwmt.infrastructure.gateway.pay;

import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayQueryResponseDTO;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayResponseDTO;
import reactor.core.publisher.Mono;

import java.util.Map;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAllInPayApiService
 * @Date: 2025/8/28 13:19
 */
public interface IAllInPayApiService {
    /**
     * 统一下单支付
     *
     * @param request 支付请求参数
     * @return 支付响应
     */
    Mono<AllInPayResponseDTO> unitOrderPay(Map<String, String> request);

    /**
     * 查询交易状态
     *
     * @param fields 查询参数
     * @return 查询响应
     */
    Mono<AllInPayQueryResponseDTO> queryTransaction(Map<String, String> fields);
}
