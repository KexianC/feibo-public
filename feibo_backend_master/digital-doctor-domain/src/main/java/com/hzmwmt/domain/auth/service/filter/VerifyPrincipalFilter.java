package com.hzmwmt.domain.auth.service.filter;

import com.hzmwmt.domain.auth.adapter.repository.IAuthRepository;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.types.design.framework.link.handler.ILogicHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VerifyPrincipalFilter
 * @Date: 2025/9/5 11:56
 */
@Slf4j
@Service
public class VerifyPrincipalFilter implements ILogicHandler<JwtRuleCommandEntity, JwtValidationRuleFilterFactory.JwtValidationContext, AuthUserAggregate> {
    @Resource
    private IAuthRepository repository;

    @Override
    public AuthUserAggregate apply(JwtRuleCommandEntity requestParameter, JwtValidationRuleFilterFactory.JwtValidationContext dynamicContext) throws Exception {
        AuthUserAggregate authUserAggregate = repository.queryUserConfig(dynamicContext.getUserId());
        if (authUserAggregate == null) {
            log.warn("JWT校验链：用户不存在。");
            throw new AppException(ResponseCode.E0005);
        }
        if (authUserAggregate.getAuthUserEntity().getStatus()) {
            log.warn("JWT校验链：用户被禁用。");
            throw new AppException(ResponseCode.E0006);
        }
        return authUserAggregate;
    }
}
