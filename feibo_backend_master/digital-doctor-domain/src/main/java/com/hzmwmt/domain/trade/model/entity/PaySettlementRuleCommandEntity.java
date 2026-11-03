package com.hzmwmt.domain.trade.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaySettlementRuleCommandEntity
 * @Date: 2025/9/2 9:28
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaySettlementRuleCommandEntity {

    /**
     * 应用ID。
     * 来源: 回调(appid), 查询(appid)
     */
    @JsonProperty("appid")
    private String appId;

    /**
     * 商户号。
     * 来源: 回调(cusid), 查询(cusid)
     */
    @JsonProperty("cusid")
    private String cusId;

    /**
     * 通联支付平台的交易流水号。
     * 来源: 回调(trxid), 查询(trxid)
     */
    @JsonProperty("trxid")
    private String trxId;

    /**
     * 【关键适配】我方系统的业务订单号。
     * 来源: 回调(cusorderid), 查询(reqsn)
     */
    @JsonProperty("cusorderid")
    @JsonAlias("reqsn") // 当Map的key是"reqsn"时，也会自动映射到此字段
    private String cusOrderId;

    /**
     * 交易状态码。
     * 来源: 回调(trxstatus), 查询(trxstatus)
     */
    @JsonProperty("trxstatus")
    private String trxStatus;

    /**
     * 交易金额，单位：分。
     * 来源: 回调(trxamt), 查询(trxamt)
     */
    @JsonProperty("trxamt")
    private Long trxAmt;

    /**
     * 【关键适配】支付完成时间，格式 yyyymmddhhmmss。
     * 来源: 回调(paytime), 查询(fintime)
     */
    @JsonProperty("paytime")
    @JsonAlias("fintime")
    private String payTime;

    /**
     * 渠道流水号 (微信、支付宝等平台的订单号)。
     * 来源: 回调(chnltrxid), 查询(chnltrxid)
     */
    @JsonProperty("chnltrxid")
    private String chnlTrxId;

    /**
     * 签名类型。
     * 来源: 回调(signtype), 查询(signtype - 通常在请求中)
     */
    @JsonProperty("signtype")
    private String signType;

    /**
     * 签名。
     * 来源: 回调(sign), 查询(sign)
     */
    @JsonProperty("sign")
    private String sign;

    /**
     * 【关键适配】错误信息。
     * 来源: 查询(retmsg, errmsg)
     */
    @JsonProperty("errmsg")
    @JsonAlias("retmsg")
    private String errMsg;

    /**
     * 营销抵扣明细 (JSON字符串会自动解析为对象列表)。
     * 来源: 回调(mktresult)
     */
    @JsonProperty("mktresult")
    private List<MktResult> mktResult;

    // --- mktresult 内部的嵌套对象定义 ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MktResult {
        @JsonProperty("id")
        private String id;
        @JsonProperty("name")
        private String name;
        @JsonProperty("tickettype")
        private String ticketType;
        @JsonProperty("cutoffvalue")
        private Long cutoffValue;
        @JsonProperty("perkvalue")
        private Long perkValue;
    }
}