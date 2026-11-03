package com.hzmwmt.domain.auth.service.filter;

import com.hzmwmt.domain.auth.adapter.port.IAuthPort;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory.JwtValidationContext;
import com.hzmwmt.types.design.framework.link.handler.ILogicHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RefreshTokenStateFilter
 * @Date: 2025/8/6 9:58
 */
@Slf4j
@Service
public class RefreshTokenStateFilter implements ILogicHandler<JwtRuleCommandEntity, JwtValidationContext, AuthUserAggregate> {

    @Resource
    private IAuthPort authPort;

    @Override
    public AuthUserAggregate apply(JwtRuleCommandEntity command, JwtValidationContext context) throws Exception {
        log.info("JWT校验链 (Refresh) - 黑名单状态校验开始");
        authPort.blacklistValidation(command.getToken());
        return next(command, context);
    }


}
