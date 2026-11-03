package com.hzmwmt.api;

import com.hzmwmt.api.dto.LockTradePayOrderRequestDTO;
import com.hzmwmt.api.dto.LockTradePayOrderResponseDTO;
import com.hzmwmt.api.dto.TradeProductResponseDTO;
import com.hzmwmt.api.response.Response;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradeApi
 * @Date: 2025/8/27 16:43
 */
public interface ITradeApi {

    /**
     * 查询交易首页的商品列表 (包含会员套餐和充值包)
     */
    Response<TradeProductResponseDTO> queryTradeIndex();

    /**
     * 锁定交易订单接口
     */
    Response<LockTradePayOrderResponseDTO> lockTradePayOrder(@Valid LockTradePayOrderRequestDTO lockTradePayOrderRequestDTO);

    /**
     * 支付回调结算接口
     */
    String paymentSuccessHandling(Map<String, String> allParams) throws Exception;
}
