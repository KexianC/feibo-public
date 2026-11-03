package com.hzmwmt.infrastructure.adapter.port;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.trade.adapter.port.ITradePort;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PayOrderEntity;
import com.hzmwmt.infrastructure.gateway.pay.IAllInPayApiService;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayQueryRequestDTO;
import com.hzmwmt.infrastructure.gateway.pay.dto.AllInPayRequestDTO;
import com.hzmwmt.infrastructure.gateway.pay.util.AllInPaySecurityUtil;
import com.hzmwmt.infrastructure.properties.AllInPayProperties;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Objects;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradePort
 * @Date: 2025/8/28 14:08
 */
@Slf4j
@Service
public class TradePort implements ITradePort {
    @Resource
    private IAllInPayApiService allInPayService;
    @Resource
    private AllInPayProperties allInPayProperties;
    @Resource
    private AllInPaySecurityUtil allInPaySecurityUtil;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public PayOrderEntity requestPayment(OrderEntity entity) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        // 将 Date 对象格式化为正确的字符串
        String expireTimeString = sdf.format(entity.getValidTime());
        AllInPayRequestDTO requestDTO = AllInPayRequestDTO.builder()
                .cusId(allInPayProperties.getCusId())
                .appId(allInPayProperties.getAppId())
                .trxAmt(entity.getTotalAmountInCents())
                .uniReqSn(entity.getOrderId().toString())
                .payType("W01")
                .body(entity.getProductName())
                .notifyUrl(allInPayProperties.getNotifyUrl())
                .cusip(allInPayProperties.getCusId())
                .validTime(allInPayProperties.getDefaultValidTime())
                .version(allInPayProperties.getVersion())
                .expireTime(expireTimeString)
                .signType(allInPayProperties.getSignType())
                .build();
        //生成盐值
        requestDTO.setRandomStr(allInPaySecurityUtil.generateRandomStr());
        // 生成签名
        String sign = allInPaySecurityUtil.generateSign(requestDTO, allInPayProperties.getRsaPrivateKeyPkcs8());
        requestDTO.setSign(sign);
        Map<String, String> formFields = allInPaySecurityUtil.convertDtoToFormMap(requestDTO);
        return allInPayService.unitOrderPay(formFields)
                .flatMap(response -> {
                    if ("SUCCESS".equals(response.getRetCode()) && "0000".equals(response.getTrxStatus())) {
                        return Mono.just(
                                PayOrderEntity.builder()
                                        .trxId(response.getTrxId())
                                        .orderId(entity.getOrderId())
                                        .url(response.getPayInfo())
                                        .build()
                        );
                    } else {
                        log.error("通联支付下单失败，retCode: {}，trxStatus: {}",
                                response.getRetCode(), response.getTrxStatus());
                        AppException exception = new AppException(ResponseCode.INTERNET_EXCEPTION);
                        return Mono.error(exception);
                    }
                })
                .retry(3)
                .block();
    }

    @Override
    public Map<String, String> queryPaymentStatus(String trxId) {
        log.info("开始[同步]查询通联订单状态 | trxId: {}", trxId);


        AllInPayQueryRequestDTO requestDTO = AllInPayQueryRequestDTO.builder()
                .cusId(allInPayProperties.getCusId())
                .appId(allInPayProperties.getAppId())
                .version(allInPayProperties.getVersion())
                .trxId(trxId)
                .signType(allInPayProperties.getSignType())
                .build();

        requestDTO.setRandomstr(allInPaySecurityUtil.generateRandomStr());
        String sign = allInPaySecurityUtil.generateSign(requestDTO, allInPayProperties.getRsaPrivateKeyPkcs8());
        requestDTO.setSign(sign);

        Map<String, String> formFields = allInPaySecurityUtil.convertDtoToFormMap(requestDTO);

        return allInPayService.queryTransaction(formFields)
                .flatMap(responseDto -> {
                    // 只做最基础的通信成功校验
                    if (!"SUCCESS".equals(responseDto.getRetCode())) {
                        String errorMsg = String.format("查询通联订单通信失败: retcode=%s, retmsg=%s",
                                responseDto.getRetCode(), responseDto.getRetMsg());
                        return Mono.error(new RuntimeException(errorMsg));
                    }

                    // 不再进行签名校验，将完整的原始响应转换为 Map
                    Map<String, String> resultMap = objectMapper.convertValue(responseDto, new TypeReference<>() {
                    });

                    return Mono.just(resultMap);
                })
                .retry(3)
                .block();
    }


    @Override
    public void authenticateNotification(Map<String, String> notificationPayload) {
        String cusorderid = notificationPayload.get("cusorderid");
        log.info("开始对支付通知进行安全认证 | cusorderid:{}", cusorderid);

        // 身份校验：核对商户号(cusid)和应用ID(appid)
        String receivedCusId = notificationPayload.get("cusid");
        String receivedAppId = notificationPayload.get("appid");
        String configuredCusId = allInPayProperties.getCusId();
        String configuredAppId = allInPayProperties.getAppId();

        if (!Objects.equals(configuredCusId, receivedCusId) || !Objects.equals(configuredAppId, receivedAppId)) {
            log.error("通知的商户号或APPID与系统配置不符！| receivedCusId:{}, receivedAppId:{}", receivedCusId, receivedAppId);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商户信息不匹配");
        }
        log.info("身份校验通过 | cusorderid:{}", cusorderid);

        // 签名校验：使用通联公钥进行验签
        String publicKey = allInPayProperties.getValidRsaPublicKey();
        if (!allInPaySecurityUtil.verifySign(notificationPayload, publicKey)) {
            log.error("支付通知签名校验失败 | cusorderid:{}", cusorderid);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "通知签名校验失败");
        }

        log.info("签名校验通过 | cusorderid:{}", cusorderid);
    }
}
