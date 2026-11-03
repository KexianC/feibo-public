package com.hzmwmt.domain.auth.service.filter;

import com.hzmwmt.domain.auth.adapter.repository.IAuthRepository;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory.JwtValidationContext;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifyPrincipalFilterSecurityTest {

    @Test
    void disabledUserIsRejectedOnRefreshValidationChainBeforeTokenIssuance() {
        IAuthRepository repository = mock(IAuthRepository.class);
        when(repository.queryUserConfig(1001L)).thenReturn(AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder().userId(1001L).status(true).build())
                .build());
        VerifyPrincipalFilter filter = new VerifyPrincipalFilter();
        ReflectionTestUtils.setField(filter, "repository", repository);

        AppException exception = assertThrows(AppException.class, () -> filter.apply(
                JwtRuleCommandEntity.builder().build(),
                JwtValidationContext.builder().userId(1001L).build()));

        assertEquals(ResponseCode.E0006.getCode(), exception.getCode());
    }
}
