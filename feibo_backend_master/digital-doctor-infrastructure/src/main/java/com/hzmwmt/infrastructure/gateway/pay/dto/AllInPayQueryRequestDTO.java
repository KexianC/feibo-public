package com.hzmwmt.infrastructure.gateway.pay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 交易查询请求 DTO
 * @ClassName AllInPayQueryRequestDTO
 * @Date: 2025/9/2 22:53
 */
@Data
@Builder
public class AllInPayQueryRequestDTO {
    /**
     * 商户号。是。
     */
    @JsonProperty("cusid")
    private String cusId;

    /**
     * 应用ID。是。
     */
    @JsonProperty("appid")
    private String appId;

    /**
     * 接口版本号, 默认 "11"。是。
     */
    @JsonProperty("version")
    private String version;

    /**
     * 商户订单号。与 trxid 二选一。否。
     */
    @JsonProperty("reqsn")
    private String reqsn;

    /**
     * 平台交易流水号。与 reqsn 二选一，推荐使用。是。
     */
    @JsonProperty("trxid")
    private String trxId;

    /**
     * 随机字符串。是。
     */
    @JsonProperty("randomstr")
    private String randomstr;

    /**
     * 签名类型。否。
     */
    @JsonProperty("signtype")
    private String signType;

    /**
     * 签名。是。
     */
    @JsonProperty("sign")
    private String sign;

}
