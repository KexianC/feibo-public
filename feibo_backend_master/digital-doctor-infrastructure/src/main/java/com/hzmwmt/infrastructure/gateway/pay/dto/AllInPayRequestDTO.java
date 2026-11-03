package com.hzmwmt.infrastructure.gateway.pay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AllInPayRequestDTO
 * @Date: 2025/8/28 12:08
 */
@Data
@Builder
public class AllInPayRequestDTO {
    /**
     * 集团/代理商商户号。如果是代理商/集团模式，则必填。是
     */
    @JsonProperty("orgid")
    private String orgId;

    /**
     * 实际进行交易的商户号。否
     */
    @JsonProperty("cusid")
    private String cusId;

    /**
     * 平台分配的APPID，用于标识是哪个应用发起的请求。否
     */
    @JsonProperty("appid")
    private String appId;

    /**
     * 交易金额，单位是分。例如，1元应传入 100。是
     */
    @JsonProperty("trxamt")
    private Long trxAmt;

    /**
     * 交易方式，决定了支付场景。例如 W01(微信扫码), A02(支付宝JS支付)。是
     */
    @JsonProperty("paytype")
    private String payType;

    /**
     * 商户交易单号。与 unireqsn 必须二选一。是
     */
    @JsonProperty("reqsn")
    private String reqSn;

    /**
     * 唯一订单号，推荐使用。与 reqsn 必须二选一。是
     */
    @JsonProperty("unireqsn")
    private String uniReqSn;

    /**
     * 订单标题/商品名称。是
     */
    @JsonProperty("body")
    private String body;

    /**
     * 附加信息。否
     */
    @JsonProperty("remark")
    private String remark;

    /**
     * 交易结果异步通知地址。必须是公网可直接访问的https地址。是
     */
    @JsonProperty("notify_url")
    private String notifyUrl;

    /**
     * 订单有效分钟数，默认5分钟，最大1440分钟。否
     */
    @JsonProperty("validtime")
    private Integer validTime;

    /**
     * 订单支付的绝对截止时间，格式 yyyyMMddHHmmss。否
     */
    @JsonProperty("expiretime")
    private String expireTime;

    /**
     * 支付限制。例如 no_credit 表示禁止使用信用卡。否
     */
    @JsonProperty("limit_pay")
    private String limitPay;

    /**
     * 支付成功后前端跳转的页面地址。仅部分JS支付支持。否
     */
    @JsonProperty("front_url")
    private String frontUrl;

    /**
     * 发起支付的用户的公网IP地址。是
     */
    @JsonProperty("cusip")
    private String cusip;

    /**
     * 支付平台用户标识，如微信的openid、支付宝的user_id。JS/小程序支付时必填。是
     */
    @JsonProperty("acct")
    private String acct;

    /**
     * 微信支付专用，传入公众号/小程序/APP自身的AppID。否
     */
    @JsonProperty("sub_appid")
    private String subAppid;

    /**
     * 分账信息，需要单独开通。否
     */
    @JsonProperty("asinfo")
    private String asInfo;

    /**
     * 支付宝花呗分期/信用卡分期专用。否
     */
    @JsonProperty("fqnum")
    private String fqNum;

    /**
     * 证件号，实名支付时必填。否
     */
    @JsonProperty("idno")
    private String idNo;

    /**
     * 付款人真实姓名，实名支付时必填。否
     */
    @JsonProperty("truename")
    private String trueName;

    /**
     * 优惠信息，JSON字符串。否
     */
    @JsonProperty("benefitdetail")
    private String benefitDetail;

    /**
     * 扩展参数，JSON字符串。否
     */
    @JsonProperty("extendparams")
    private String extendParams;

    /**
     * 版本号，默认填 11。是
     */
    @JsonProperty("version")
    private String version;

    /**
     * 随机字符串，用于签名。是
     */
    @JsonProperty("randomstr")
    private String randomStr;

    /**
     * 签名方式，RSA 或 SM2。否
     */
    @JsonProperty("signtype")
    private String signType;

    /**
     * 请求签名，对所有非空参数排序后生成。是
     */
    @JsonProperty("sign")
    private String sign;
}
