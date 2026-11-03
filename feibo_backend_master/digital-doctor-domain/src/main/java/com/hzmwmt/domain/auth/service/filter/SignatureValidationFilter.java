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
 * @Description: TODO 签名校验
 * @ClassName SignatureValidationFilter
 * @Date: 2025/8/5 16:42
 */
@Slf4j
@Service
public class SignatureValidationFilter implements ILogicHandler<JwtRuleCommandEntity, JwtValidationContext, AuthUserAggregate> {

    @Resource
    private IAuthPort authPort;

    @Override
    public AuthUserAggregate apply(JwtRuleCommandEntity command, JwtValidationContext context) throws Exception {
        Long userId = authPort.signatureValidation(command);
        context.setUserId(userId);
        return next(command, context);
    }
}
