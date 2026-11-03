package com.hzmwmt.infrastructure.gateway.pay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AllInPayQueryResponseDTO
 * @Date: 2025/9/2 22:55
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllInPayQueryResponseDTO {
    /**
     * 返回码，SUCCESS/FAIL。此字段是通信标识，非交易结果。
     */
    @JsonProperty("retcode")
    private String retCode;

    /**
     * 返回码说明。
     */
    @JsonProperty("retmsg")
    private String retMsg;

    /**
     * 商户号。
     */
    @JsonProperty("cusid")
    private String cusId;

    /**
     * 应用ID。
     */
    @JsonProperty("appid")
    private String appId;

    /**
     * 平台的交易流水号。
     */
    @JsonProperty("trxid")
    private String trxId;

    /**
     * 支付渠道的交易单号 (如支付宝,微信平台的交易单号)。
     */
    @JsonProperty("chnltrxid")
    private String chnlTrxId;

    /**
     * 商户的交易订单号。
     */
    @JsonProperty("reqsn")
    private String reqsn;

    /**
     * 交易类型。
     */
    @JsonProperty("trxcode")
    private String trxCode;

    /**
     * 交易金额，单位为分。
     */
    @JsonProperty("trxamt")
    private Long trxAmt;

    /**
     * 交易的状态码。如果为空,则交易正在处理中。
     */
    @JsonProperty("trxstatus")
    private String trxStatus;

    /**
     * 支付平台用户标识 (openid, user_id等)。
     */
    @JsonProperty("acct")
    private String acct;

    /**
     * 交易完成时间，格式 yyyyMMddHHmmss。
     */
    @JsonProperty("fintime")
    private String finTime;

    /**
     * 随机字符串。
     */
    @JsonProperty("randomstr")
    private String randomStr;

    /**
     * 失败的原因说明。
     */
    @JsonProperty("errmsg")
    private String errMsg;

    /**
     * 渠道子商户号 (限微信/支付宝交易响应)。
     */
    @JsonProperty("cmid")
    private String cmid;

    /**
     * 渠道号 (限微信交易响应)。
     */
    @JsonProperty("chnlid")
    private String chnlId;

    /**
     * 原交易金额，与请求字段trxamt值一致。
     */
    @JsonProperty("initamt")
    private String initAmt;

    /**
     * 手续费，单位：分。
     */
    @JsonProperty("fee")
    private Long fee;

    /**
     * 渠道信息 (目前返回云闪付/微信/支付宝的活动参数)。
     */
    @JsonProperty("chnldata")
    private String chnlData;

    /**
     * 借贷标识 (00-借记卡, 02-信用卡, 99-其他)。
     */
    @JsonProperty("accttype")
    private String acctType;

    /**
     * 所属银行。
     */
    @JsonProperty("bankcode")
    private String bankCode;

    /**
     * 买家账号 (支付宝返回logonid, 云闪付返回userid)。
     */
    @JsonProperty("logonid")
    private String logonId;

    /**
     * 分期数。
     */
    @JsonProperty("fqnum")
    private String fqNum;

    /**
     * 签名，用于对响应进行验签。
     */
    @JsonProperty("sign")
    private String sign;

    /**
     * 结算周期 (1预收, 2日结, 4月结, 8向持卡人收)。
     */
    @JsonProperty("feecycle")
    private String feeCycle;

    /**
     * 交易备注，订单支付的业务关联内容。
     */
    @JsonProperty("trxreserve")
    private String trxReserve;

    /**
     * 通联渠道侧OPENID。
     */
    @JsonProperty("tlopenid")
    private String tlOpenId;

    /**
     * 分期商户贴息手续费。
     */
    @JsonProperty("feefq")
    private Long feeFq;

}
