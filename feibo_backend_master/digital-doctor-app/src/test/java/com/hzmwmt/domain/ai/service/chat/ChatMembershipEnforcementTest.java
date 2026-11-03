package com.hzmwmt.domain.ai.service.chat;

import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.aggregate.*;
import com.hzmwmt.domain.ai.model.entity.*;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.model.IModelStrategyService;
import com.hzmwmt.domain.ai.service.chat.model.factory.DefaultModelStrategyFactory;
import com.hzmwmt.domain.ai.service.chat.model.strategy.AbstractModelStrategyService;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import reactor.core.Disposables;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ChatMembershipEnforcementTest {
    @Test void ordinaryModelsNeverTouchMembershipOrLegacyQuota() {
        Fixture f = new Fixture(false, request -> request.getOnCompleteWithResult().accept("回答"));
        f.service.completions(input());
        verifyNoInteractions(f.quotas);
        verify(f.repository, never()).deductDoctorAiChatAccountQuota(anyLong());
        verify(f.repository, never()).deductDoctorAssistantAiChatAccountQuota(anyLong());
    }
    @Test void anExhaustedPremiumAllowanceStopsBeforeProviderInvocation() {
        Fixture f = new Fixture(true, request -> fail("Provider must not be invoked"));
        doThrow(new AppException("QUOTA_EXCEEDED", "今日高级对话额度已用完"))
                .when(f.quotas).reserve(eq(42L), eq("PREMIUM_CHAT"), eq(1L), anyString());
        f.service.completions(input());
        verify(f.strategy, never()).doMessageResponse(any());
        verify(f.quotas, never()).commit(anyLong(), anyString());
    }
    @Test void failureBeforeAnyAnswerRefundsTheHoldExactlyOnce() {
        Fixture f = new Fixture(true, request -> {
            request.getOnError().accept(new IllegalStateException("provider failed"));
            request.getOnError().accept(new IllegalStateException("duplicate completion"));
        });
        f.service.completions(input());
        verify(f.quotas).reserve(eq(42L), eq("PREMIUM_CHAT"), eq(1L), anyString());
        verify(f.quotas).release(eq(42L), anyString());
        verify(f.quotas, never()).commit(anyLong(), anyString());
    }
    @Test void partialAnswerCommitsBeforeDeliveryAndCannotBeRefundedByLaterFailure() {
        Fixture f = new Fixture(true, request -> {
            request.getOnNext().accept("reply", "已生成的回答");
            request.getOnError().accept(new IllegalStateException("connection closed"));
        });
        f.service.completions(input());
        var order = inOrder(f.quotas);
        order.verify(f.quotas).reserve(eq(42L), eq("PREMIUM_CHAT"), eq(1L), anyString());
        order.verify(f.quotas).commit(eq(42L), anyString());
        verify(f.quotas, never()).release(anyLong(), anyString());
    }
    @Test void fallbackControlEventAloneNeverConsumesTheReservedAllowance() {
        Fixture f = new Fixture(true, request -> {
            request.getOnNext().accept("model_fallback", "{\"provider\":\"deepseek\"}");
            request.getOnError().accept(new IllegalStateException("fallback unavailable"));
        });
        f.service.completions(input());
        verify(f.quotas, never()).commit(anyLong(), anyString());
        verify(f.quotas).release(eq(42L), anyString());
    }
    @Test void primaryAndFallbackReplyConsumeOnlyOneAllowance() {
        Fixture f = new Fixture(true, request -> {
            request.getOnNext().accept("reply", "partial primary answer");
            request.getOnNext().accept("model_fallback", "{\"provider\":\"deepseek\"}");
            request.getOnNext().accept("reply", "replacement answer");
            request.getOnCompleteWithResult().accept("replacement answer");
        });
        f.service.completions(input());
        verify(f.quotas).reserve(eq(42L), eq("PREMIUM_CHAT"), eq(1L), anyString());
        verify(f.quotas).commit(eq(42L), anyString());
        verify(f.quotas, never()).release(anyLong(), anyString());
        verify(f.repository).saveChatHistory(argThat(history -> "replacement answer".equals(history.getResult())));
    }
    private static ChatProcessAggregate input() {
        return ChatProcessAggregate.builder().userId(42L)
                .aiMessageEntity(AIMessageEntity.builder().content("请介绍健康科普").aiModelConfigId(1L).build()).build();
    }
    private static class Fixture {
        final IEntitlementService quotas = mock(IEntitlementService.class);
        final IAIRepository repository = mock(IAIRepository.class);
        final IModelStrategyService strategy = mock(IModelStrategyService.class);
        final ChatService service;
        Fixture(boolean premium, Consumer<ModelProcessAggregate> execution) {
            service = new ChatService() {
                @Override protected RuleLogicEntity<AIChatRuleCommandAggregate> doCheckLogic(AIChatRuleCommandAggregate aggregate, String... logics) {
                    aggregate.setAiModelConfigLeafEntity(AIModelConfigEntity.builder().type(premium).name("test").modelCode("test").build());
                    return RuleLogicEntity.<AIChatRuleCommandAggregate>builder().type(LogicCheckTypeVO.SUCCESS).data(aggregate).build();
                }
            };
            service.entitlementService = quotas; service.repository = repository;
            service.defaultModelStrategyFactory = mock(DefaultModelStrategyFactory.class);
            when(service.defaultModelStrategyFactory.getStrategyService("test")).thenReturn(strategy);
            when(service.defaultModelStrategyFactory.getAbstractStrategyService("test")).thenReturn(mock(AbstractModelStrategyService.class));
            when(strategy.doMessageResponse(any())).thenAnswer(invocation -> {
                execution.accept(invocation.getArgument(0)); return Disposables.single();
            });
        }
    }
}
