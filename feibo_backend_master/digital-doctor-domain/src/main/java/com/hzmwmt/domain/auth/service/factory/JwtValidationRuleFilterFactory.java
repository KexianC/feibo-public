package com.hzmwmt.domain.auth.service.factory;


import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.filter.RefreshTokenStateFilter;
import com.hzmwmt.domain.auth.service.filter.RequiredFieldsFilter;
import com.hzmwmt.domain.auth.service.filter.SignatureValidationFilter;
import com.hzmwmt.domain.auth.service.filter.VerifyPrincipalFilter;
import com.hzmwmt.types.design.framework.link.LinkArmory;
import com.hzmwmt.types.design.framework.link.chain.BusinessLinkedList;
import com.hzmwmt.types.util.SpringUtil;
import lombok.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName JwtValidationRuleFilterFactory
 * @Date: 2025/8/5 17:23
 */
@Component
public class JwtValidationRuleFilterFactory {

    public BusinessLinkedList<JwtRuleCommandEntity, JwtValidationContext, AuthUserAggregate> getValidationChain(TokenTypeEnumVO type) {
        return switch (type) {
            case ACCESS, REFRESH -> SpringUtil.getBean(type.getFilterChainName(), BusinessLinkedList.class);
            default -> throw new IllegalArgumentException("未定义的Token类型");
        };
    }

    // 负责构建实例
    @Bean("accessTokenValidationChain")
    public BusinessLinkedList<JwtRuleCommandEntity, JwtValidationContext, AuthUserAggregate> createAccessTokenValidationChain(
            RequiredFieldsFilter requiredFieldsFilter,
            SignatureValidationFilter signatureValidationFilter,
            VerifyPrincipalFilter verifyPrincipalFilter
    ) {
        return new LinkArmory<>(
                "Access Token校验链",
                requiredFieldsFilter,
                signatureValidationFilter,
                verifyPrincipalFilter
        ).getLogicLink();
    }

    @Bean("refreshTokenValidationChain")
    public BusinessLinkedList<JwtRuleCommandEntity, JwtValidationContext, AuthUserAggregate> createRefreshTokenValidationChain(
            RequiredFieldsFilter requiredFieldsFilter,
            SignatureValidationFilter signatureValidationFilter,
            RefreshTokenStateFilter refreshTokenStateFilter,
            VerifyPrincipalFilter verifyPrincipalFilter
    ) {
        return new LinkArmory<>(
                "Refresh Token校验链",
                requiredFieldsFilter,
                signatureValidationFilter,
                refreshTokenStateFilter,
                verifyPrincipalFilter
        ).getLogicLink();
    }


    @Getter
    @AllArgsConstructor
    public enum TokenTypeEnumVO {

        ACCESS("accessTokenValidationChain"),
        REFRESH("refreshTokenValidationChain");


        private final String filterChainName;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JwtValidationContext {
        private Long userId;
    }

}
