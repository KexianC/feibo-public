package com.hzmwmt.infrastructure.gateway.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ALiYunSMSGateway
 * @Date: 2025/9/16 13:59
 */
@Slf4j
@Service
public class ALiYunSMSGateway implements ISMSGateway {
    @Resource
    Client client;

    @Override
    public void sendShortMessage(String phoneNum, String content) {
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setPhoneNumbers(phoneNum)
                .setSignName("飞驳科技")
                .setTemplateCode("SMS_294092488")
                .setTemplateParam(content);
        try {
            SendSmsResponse sendSmsResponse = client.sendSms(sendSmsRequest);
            log.info("准备发送短信: templateParamLength={}", content == null ? 0 : content.length());
            if ("OK".equals(sendSmsResponse.getBody().getCode())) {
                log.info("短信发送成功！RequestId: {}", sendSmsResponse.getBody().getRequestId());
            } else {
                log.error("短信发送失败: status={}", sendSmsResponse.getBody().getCode());
                throw new AppException(ResponseCode.SERVICE_ERROR.getCode(), "短信发送失败");
            }
        } catch (Exception e) {
            log.error("发送短信异常: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.INTERNET_EXCEPTION.getCode(), "短信发送失败");
        }

    }

}
