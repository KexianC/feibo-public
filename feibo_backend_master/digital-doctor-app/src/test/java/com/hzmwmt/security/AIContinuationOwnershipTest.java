package com.hzmwmt.security;

import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ChatProcessAggregate;
import com.hzmwmt.domain.ai.model.aggregate.CreateChatHistoryAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.ChatService;
import com.hzmwmt.domain.ai.service.chat.model.IModelStrategyService;
import com.hzmwmt.domain.ai.service.chat.model.factory.DefaultModelStrategyFactory;
import com.hzmwmt.domain.ai.service.chat.model.strategy.AbstractModelStrategyService;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import com.hzmwmt.domain.ai.service.chat.rule.filter.ParameterValidationFilter;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIContinuationOwnershipTest {

    private static final Long USER_A = 1001L;
    private static final Long USER_B = 2002L;
    private static final Long CONVERSATION_A = 3003L;
    private static final Long MODEL_ID = 4004L;

    @Mock
    private IAIRepository repository;
    @Mock
    private IAIPort aiPort;
    @Mock
    private AIChatValidationFactory validationFactory;
    @Mock
    private DefaultModelStrategyFactory modelStrategyFactory;

    private ChatService chatService;
    private ParameterValidationFilter parameterValidationFilter;

    @BeforeEach
    void setUp() {
        parameterValidationFilter = new ParameterValidationFilter();
        ReflectionTestUtils.setField(parameterValidationFilter, "aiRepository", repository);

        chatService = new ChatService();
        ReflectionTestUtils.setField(chatService, "repository", repository);
        ReflectionTestUtils.setField(chatService, "aiPort", aiPort);
        ReflectionTestUtils.setField(chatService, "aiChatValidationFactory", validationFactory);
        ReflectionTestUtils.setField(chatService, "defaultModelStrategyFactory", modelStrategyFactory);

    }

    @Test
    void foreignConversationIsRejectedBeforeModelQuotaOrPersistence() {
        when(repository.validateAiConversationId(USER_B, CONVERSATION_A)).thenReturn(false);
        when(validationFactory.openLogicFilter()).thenReturn(Map.of(
                AIChatValidationFactory.LogicModel.PARAMETER_VALIDATION.getCode(), parameterValidationFilter
        ));

        chatService.completions(ChatProcessAggregate.builder()
                .userId(USER_B)
                .resourceUserTypeEnumVO(ResourceUserTypeEnumVO.DOCTOR)
                .isAllHistory(true)
                .aiMessageEntity(AIMessageEntity.builder()
                        .aiConversationId(CONVERSATION_A)
                        .aiModelConfigId(MODEL_ID)
                        .content("追加别人会话的消息")
                        .build())
                .build());

        verify(repository).validateAiConversationId(USER_B, CONVERSATION_A);
        verify(repository, never()).queryAiModelLeafConfig(MODEL_ID);
        verify(repository, never()).deductDoctorAiChatAccountQuota(USER_B);
        verify(repository, never()).deductDoctorAssistantAiChatAccountQuota(USER_B);
        verify(repository, never()).queryAiMessageHistory(USER_B, CONVERSATION_A, MODEL_ID, true);
        verify(repository, never()).saveChatHistory(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(modelStrategyFactory);
    }

    @Test
    void ownerConversationContinuesThroughModelAndOwnerBoundPersistence() throws Exception {
        AIModelConfigEntity modelConfig = AIModelConfigEntity.builder()
                .aiModelConfigId(MODEL_ID)
                .modelCode("test-model")
                .name("测试模型")
                .type(false)
                .build();
        IAIChatRulerLogicFilter contentValidation = mock(IAIChatRulerLogicFilter.class);
        AbstractModelStrategyService tokenStrategy = mock(AbstractModelStrategyService.class);
        IModelStrategyService modelStrategy = mock(IModelStrategyService.class);
        Disposable disposable = mock(Disposable.class);

        when(repository.validateAiConversationId(USER_A, CONVERSATION_A)).thenReturn(true);
        when(repository.queryAiModelLeafConfig(MODEL_ID)).thenReturn(modelConfig);
        when(contentValidation.filter(any(AIChatRuleCommandAggregate.class))).thenAnswer(invocation -> {
            AIChatRuleCommandAggregate command = invocation.getArgument(0);
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.SUCCESS)
                    .data(command)
                    .build();
        });
        when(validationFactory.openLogicFilter()).thenReturn(Map.of(
                AIChatValidationFactory.LogicModel.PARAMETER_VALIDATION.getCode(), parameterValidationFilter,
                AIChatValidationFactory.LogicModel.CONTENT_VALIDATION.getCode(), contentValidation
        ));
        when(modelStrategyFactory.getAbstractStrategyService("test-model")).thenReturn(tokenStrategy);
        when(tokenStrategy.calculateTotalTokens(any(AIMessageEntity.class))).thenReturn(2);
        when(modelStrategyFactory.getStrategyService("test-model")).thenReturn(modelStrategy);
        when(repository.saveChatHistory(any(CreateChatHistoryAggregate.class))).thenReturn(CONVERSATION_A);
        when(modelStrategy.doMessageResponse(any(ModelProcessAggregate.class))).thenAnswer(invocation -> {
            ModelProcessAggregate process = invocation.getArgument(0);
            process.getOnCompleteWithResult().accept("正常回复");
            return disposable;
        });

        chatService.completions(ChatProcessAggregate.builder()
                .userId(USER_A)
                .resourceUserTypeEnumVO(ResourceUserTypeEnumVO.DOCTOR)
                .isAllHistory(true)
                .aiMessageEntity(AIMessageEntity.builder()
                        .aiConversationId(CONVERSATION_A)
                        .aiModelConfigId(MODEL_ID)
                        .content("用户A正常续聊")
                        .build())
                .build());

        org.mockito.ArgumentCaptor<CreateChatHistoryAggregate> saved =
                org.mockito.ArgumentCaptor.forClass(CreateChatHistoryAggregate.class);
        verify(modelStrategy).doMessageResponse(any(ModelProcessAggregate.class));
        verify(repository).saveChatHistory(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_A);
        assertThat(saved.getValue().getAiUserMessageEntity().getAiConversationId())
                .isEqualTo(CONVERSATION_A);
        verify(repository, never()).deductDoctorAiChatAccountQuota(USER_A);
        verify(repository, never()).deductDoctorAssistantAiChatAccountQuota(USER_A);
    }
}
