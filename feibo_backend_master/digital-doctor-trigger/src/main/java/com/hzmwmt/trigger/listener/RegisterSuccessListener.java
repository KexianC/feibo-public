package com.hzmwmt.trigger.listener;

import com.google.common.eventbus.Subscribe;
import com.hzmwmt.domain.user.event.RegisterSuccessMessageEvent;
import com.hzmwmt.domain.user.service.IUserResourceService;
import com.hzmwmt.types.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RegisterSuccessListener
 * @Date: 2025/9/8 12:11
 */
@Slf4j
@Component
public class RegisterSuccessListener {
    @Subscribe
    public void handleEvent(RegisterSuccessMessageEvent.RegisterSuccessMessage registerSuccessMessage) {
        log.info("用户注册成功消息处理开始");
        try {
            //防止循环依赖
            IUserResourceService bean = SpringUtil.getBean(IUserResourceService.class);
            bean.bindDefaultAiMembershipPlan(
                    registerSuccessMessage.getUserId()
            );
        } catch (Exception e) {
            log.error("用户注册成功消息处理异常: type={}", e.getClass().getSimpleName());
            //加一个event表处理异常情况
        }
    }
}
