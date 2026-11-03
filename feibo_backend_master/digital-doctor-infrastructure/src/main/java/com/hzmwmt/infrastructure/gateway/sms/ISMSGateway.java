package com.hzmwmt.infrastructure.gateway.sms;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ISMSGateway
 * @Date: 2025/9/16 13:58
 */
public interface ISMSGateway {
    void sendShortMessage(String phoneNum, String content);
}
