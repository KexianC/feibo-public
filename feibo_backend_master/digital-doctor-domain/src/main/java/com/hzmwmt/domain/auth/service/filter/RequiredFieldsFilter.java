package com.hzmwmt.domain.auth.service.filter;

import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.types.design.framework.link.handler.ILogicHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RequiredFieldsFilter
 * @Date: 2025/9/5 12:31
 */
@Slf4j
@Service
public class RequiredFieldsFilter implements ILogicHandler<JwtRuleCommandEntity, JwtValidationRuleFilterFactory.JwtValidationContext, AuthUserAggregate> {
    @Override
    public AuthUserAggregate apply(JwtRuleCommandEntity command, JwtValidationRuleFilterFactory.JwtValidationContext dynamicContext) throws Exception {
        if (command.getToken() == null) {
            log.warn("JWT校验链：Token为空。");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        if (command.getClientFingerprint() == null) {
            log.warn("JWT校验链：客户端指纹为空。");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        return next(command, dynamicContext);
    }
}
