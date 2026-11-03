package com.hzmwmt.domain.user.event;

import com.hzmwmt.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RegisterSuccessMessageEvent
 * @Date: 2025/9/8 10:29
 */
@Component
public class RegisterSuccessMessageEvent extends BaseEvent<RegisterSuccessMessageEvent.RegisterSuccessMessage> {
    @Override
    public EventMessage<RegisterSuccessMessageEvent.RegisterSuccessMessage> buildEventMessage(RegisterSuccessMessageEvent.RegisterSuccessMessage data) {
        return EventMessage.<RegisterSuccessMessageEvent.RegisterSuccessMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    //后续可以优化为rocketMQ
    @Override
    public String topic() {
        return "ai_resource_bind";
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegisterSuccessMessage {
        private Long userId;
    }

}
