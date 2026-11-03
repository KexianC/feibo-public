package com.hzmwmt.security;

import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.filter.ParameterValidationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIParameterValidationOwnershipTest {

    private static final Long USER_A = 1001L;
    private static final Long USER_B = 2002L;
    private static final Long CONVERSATION_A = 3003L;
    private static final Long MODEL_ID = 4004L;

    @Mock
    private IAIRepository repository;

    private ParameterValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ParameterValidationFilter();
        ReflectionTestUtils.setField(filter, "aiRepository", repository);
    }

    @Test
    void ownerCanContinueConversation() throws Exception {
        AIModelConfigEntity model = AIModelConfigEntity.builder()
                .aiModelConfigId(MODEL_ID)
                .modelCode("test-model")
                .build();
        when(repository.validateAiConversationId(USER_A, CONVERSATION_A)).thenReturn(true);
        when(repository.queryAiModelLeafConfig(MODEL_ID)).thenReturn(model);

        RuleLogicEntity<AIChatRuleCommandAggregate> result = filter.filter(command(USER_A));

        assertThat(result.getType()).isEqualTo(LogicCheckTypeVO.SUCCESS);
        assertThat(result.getData().getAiModelConfigLeafEntity()).isSameAs(model);
        verify(repository).validateAiConversationId(USER_A, CONVERSATION_A);
    }

    @Test
    void foreignOrMissingConversationIsRejectedBeforeModelLookup() throws Exception {
        when(repository.validateAiConversationId(USER_B, CONVERSATION_A)).thenReturn(false);

        RuleLogicEntity<AIChatRuleCommandAggregate> result = filter.filter(command(USER_B));

        assertThat(result.getType()).isEqualTo(LogicCheckTypeVO.REFUSE);
        assertThat(result.getInfo()).isEqualTo("会话不存在");
        verify(repository).validateAiConversationId(USER_B, CONVERSATION_A);
        verify(repository, never()).queryAiModelLeafConfig(MODEL_ID);
    }

    private AIChatRuleCommandAggregate command(Long userId) {
        return AIChatRuleCommandAggregate.builder()
                .userId(userId)
                .aiMessageEntity(AIMessageEntity.builder()
                        .aiConversationId(CONVERSATION_A)
                        .aiModelConfigId(MODEL_ID)
                        .content("继续问诊")
                        .build())
                .build();
    }
}
