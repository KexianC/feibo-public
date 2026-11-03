package com.hzmwmt.infrastructure.gateway.pay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO @JsonIgnoreProperties(ignoreUnknown = true) 注解确保了向前兼容性， 即使通联API在未来增加新字段，也不会导致反序列化失败。
 * @ClassName AllInPayResponseDTO
 * @Date: 2025/8/28 13:17
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllInPayResponseDTO {

    /**
     * 返回码。SUCCESS/FAIL。此字段是通信标识，非交易结果。否。
     */
    @JsonProperty("retcode")
    private String retCode;

    /**
     * 返回码说明。是。
     */
    @JsonProperty("retmsg")
    private String retMsg;

    /**
     * 商户号。平台分配的商户号。否。
     */
    @JsonProperty("cusid")
    private String cusId;

    /**
     * 应用ID。平台分配的APPID。否。
     */
    @JsonProperty("appid")
    private String appId;

    /**
     * 交易单号。收银宝平台的交易流水号。否。
     */
    @JsonProperty("trxid")
    private String trxId;

    /**
     * 渠道平台交易单号，例如微信、支付宝平台的交易单号。是。
     */
    @JsonProperty("chnltrxid")
    private String chnlTrxId;

    /**
     * 商户交易单号，即您请求时传入的 reqsn 或 unireqsn。否。
     */
    @JsonProperty("reqsn")
    private String reqSn;

    /**
     * 随机字符串。随机生成的字符串。否。
     */
    @JsonProperty("randomstr")
    private String randomStr;

    /**
     * 交易状态。对于刷卡支付，该状态表示实际的支付结果，其他为下单状态。否。
     */
    @JsonProperty("trxstatus")
    private String trxStatus;

    /**
     * 交易完成时间，格式 yyyyMMddHHmmss。是。
     */
    @JsonProperty("fintime")
    private String finTime;

    /**
     * 错误原因。失败的原因说明。是。
     */
    @JsonProperty("errmsg")
    private String errMsg;

    /**
     * 支付串。扫码支付则返回二维码串，js支付则返回json字符串等。是。
     */
    @JsonProperty("payinfo")
    private String payInfo;

    /**
     * 交易类型。是。
     */
    @JsonProperty("trxcode")
    private String trxCode;

    /**
     * 签名。用于对响应进行验签。否。
     */
    @JsonProperty("sign")
    private String sign;

    /**
     * 交易金额，单位为分。（从之前版本保留，查询和通知接口通常会返回）
     */
    @JsonProperty("trxamt")
    private Long trxAmt;

}
