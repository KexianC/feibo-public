package com.hzmwmt.security;

import com.hzmwmt.domain.ai.model.aggregate.CreateChatHistoryAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.infrastructure.adapter.repository.AIRepository;
import com.hzmwmt.infrastructure.dao.AIConversationMapper;
import com.hzmwmt.infrastructure.dao.AIMessageMapper;
import com.hzmwmt.infrastructure.dao.AIModelConfigMapper;
import com.hzmwmt.infrastructure.dao.po.AIConversation;
import com.hzmwmt.infrastructure.dao.po.AIMessage;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIRepositoryConversationOwnershipTest {

    private static final Long USER_A = 1001L;
    private static final Long USER_B = 2002L;
    private static final Long CONVERSATION_A = 3003L;
    private static final Long MODEL_ID = 4004L;

    @Mock
    private AIConversationMapper conversationMapper;
    @Mock
    private AIMessageMapper messageMapper;
    @Mock
    private AIModelConfigMapper modelConfigMapper;

    private AIRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AIRepository();
        ReflectionTestUtils.setField(repository, "aiConversationMapper", conversationMapper);
        ReflectionTestUtils.setField(repository, "aiMessageMapper", messageMapper);
        ReflectionTestUtils.setField(repository, "aiModelConfigMapper", modelConfigMapper);
    }

    @Test
    void ownerCanReadIndexAndHistoryThroughUserScopedMapperCalls() {
        when(conversationMapper.validateAiConversationIdAndUserId(USER_A, CONVERSATION_A)).thenReturn(true);
        when(messageMapper.selectAiMessageListCursorPageByConversationIdAndUserId(
                USER_A, CONVERSATION_A, null, 11
        )).thenReturn(List.of(message(5005L, "用户A的消息")));
        when(messageMapper.selectAiMessageHistoryByConversationIdAndUserIdAndAiModelConfigId(
                USER_A, CONVERSATION_A, MODEL_ID, true
        )).thenReturn(List.of(message(5005L, "用户A的历史")));

        CursorPageResponseData<AIMessageEntity> index = repository.queryAiMessageIndex(
                USER_A, CONVERSATION_A, null, 10
        );
        List<AIMessageEntity> history = repository.queryAiMessageHistory(
                USER_A, CONVERSATION_A, MODEL_ID, true
        );

        assertThat(index.getData()).extracting(AIMessageEntity::getContent)
                .containsExactly("用户A的消息");
        assertThat(history).extracting(AIMessageEntity::getContent)
                .containsExactly("用户A的历史");
        verify(messageMapper).selectAiMessageListCursorPageByConversationIdAndUserId(
                USER_A, CONVERSATION_A, null, 11
        );
        verify(messageMapper).selectAiMessageHistoryByConversationIdAndUserIdAndAiModelConfigId(
                USER_A, CONVERSATION_A, MODEL_ID, true
        );
    }

    @Test
    void foreignConversationReadAndHistoryUseSameNotFoundStyleFailure() {
        when(conversationMapper.validateAiConversationIdAndUserId(USER_B, CONVERSATION_A)).thenReturn(false);

        assertConversationNotFound(() -> repository.queryAiMessageIndex(
                USER_B, CONVERSATION_A, null, 10
        ));
        assertConversationNotFound(() -> repository.queryAiMessageHistory(
                USER_B, CONVERSATION_A, MODEL_ID, true
        ));

        verifyNoInteractions(messageMapper);
    }

    @Test
    void foreignConversationCannotAppendMessagesOrUpdateBilling() {
        when(conversationMapper.validateAiConversationIdAndUserId(USER_B, CONVERSATION_A)).thenReturn(false);

        assertConversationNotFound(() -> repository.saveChatHistory(historyAggregate(USER_B)));

        verify(conversationMapper, never()).selectConsumptionByAiConversationIdAndUserId(USER_B, CONVERSATION_A);
        verify(conversationMapper, never()).selectBillingByAiConversationIdAndUserId(USER_B, CONVERSATION_A);
        verify(conversationMapper, never()).updateConversationByAiConversationIdAndUserId(
                org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(messageMapper, modelConfigMapper);
    }

    @Test
    void ownerAppendScopesConsumptionBillingUpdateAndMessagesToOwner() {
        stubSuccessfulAppend(visibleRuntimeModel());

        Long result = repository.saveChatHistory(historyAggregate(USER_A));

        assertThat(result).isEqualTo(CONVERSATION_A);
        ArgumentCaptor<AIConversation> update = ArgumentCaptor.forClass(AIConversation.class);
        verify(conversationMapper).updateConversationByAiConversationIdAndUserId(update.capture());
        assertThat(update.getValue().getUserId()).isEqualTo(USER_A);
        assertThat(update.getValue().getAiConversationId()).isEqualTo(CONVERSATION_A);
        verify(conversationMapper).selectConsumptionByAiConversationIdAndUserId(USER_A, CONVERSATION_A);
        verify(conversationMapper).selectBillingByAiConversationIdAndUserId(USER_A, CONVERSATION_A);
        verify(messageMapper).insert(anyList());
        InOrder lockBeforeWrites = inOrder(
                modelConfigMapper, conversationMapper, messageMapper);
        lockBeforeWrites.verify(modelConfigMapper)
                .selectAiModelHistoryConfigByAiModelConfigIdForUpdate(MODEL_ID);
        lockBeforeWrites.verify(conversationMapper)
                .selectConsumptionByAiConversationIdAndUserId(USER_A, CONVERSATION_A);
        lockBeforeWrites.verify(conversationMapper)
                .updateConversationByAiConversationIdAndUserId(
                        org.mockito.ArgumentMatchers.any(AIConversation.class));
        lockBeforeWrites.verify(messageMapper).insert(anyList());
    }

    @Test
    void inFlightAppendStillPersistsAfterModelBecomesACommittedTombstone() {
        AIModelConfig tombstone = visibleRuntimeModel();
        tombstone.setVisible(false);
        tombstone.setIsDeleted(true);
        tombstone.setApiKeyCiphertext(null);
        stubSuccessfulAppend(tombstone);

        Long result = repository.saveChatHistory(historyAggregate(USER_A));

        assertThat(result).isEqualTo(CONVERSATION_A);
        verify(modelConfigMapper)
                .selectAiModelHistoryConfigByAiModelConfigIdForUpdate(MODEL_ID);
        verify(modelConfigMapper, never())
                .selectAiModelLeafConfigByAiModelConfigId(MODEL_ID);
        verify(conversationMapper)
                .updateConversationByAiConversationIdAndUserId(
                        org.mockito.ArgumentMatchers.any(AIConversation.class));
        verify(messageMapper).insert(anyList());
    }

    @Test
    void appendFailsBeforeAnyMutationWhenLockedVisibleModelNoLongerExists() {
        when(conversationMapper.validateAiConversationIdAndUserId(USER_A, CONVERSATION_A))
                .thenReturn(true);
        when(modelConfigMapper.selectAiModelHistoryConfigByAiModelConfigIdForUpdate(MODEL_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> repository.saveChatHistory(historyAggregate(USER_A)))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getInfo())
                        .isEqualTo("AI 模型运行配置不可用"));

        verify(modelConfigMapper)
                .selectAiModelHistoryConfigByAiModelConfigIdForUpdate(MODEL_ID);
        verify(modelConfigMapper, never())
                .selectAiModelLeafConfigByAiModelConfigId(MODEL_ID);
        verify(conversationMapper, never())
                .selectConsumptionByAiConversationIdAndUserId(USER_A, CONVERSATION_A);
        verify(conversationMapper, never())
                .selectBillingByAiConversationIdAndUserId(USER_A, CONVERSATION_A);
        verify(conversationMapper, never())
                .updateConversationByAiConversationIdAndUserId(
                        org.mockito.ArgumentMatchers.any(AIConversation.class));
        verifyNoInteractions(messageMapper);
    }

    private AIMessage message(Long id, String content) {
        return AIMessage.builder()
                .aiMessageId(id)
                .aiConversationId(CONVERSATION_A)
                .aiModelConfigId(MODEL_ID)
                .role(false)
                .content(content)
                .build();
    }

    private CreateChatHistoryAggregate historyAggregate(Long userId) {
        return CreateChatHistoryAggregate.builder()
                .userId(userId)
                .isNewConversation(false)
                .result("回复")
                .aiUserMessageEntity(AIMessageEntity.builder()
                        .aiConversationId(CONVERSATION_A)
                        .aiModelConfigId(MODEL_ID)
                        .content("追加消息")
                        .token(2L)
                        .aiToken(3L)
                        .build())
                .build();
    }

    private AIModelConfig visibleRuntimeModel() {
        return AIModelConfig.builder()
                .aiModelConfigId(MODEL_ID)
                .isLeaf(true)
                .visible(true)
                .modelCode("test-model")
                .name("测试模型")
                .build();
    }

    private void stubSuccessfulAppend(AIModelConfig historyModel) {
        when(conversationMapper.validateAiConversationIdAndUserId(USER_A, CONVERSATION_A))
                .thenReturn(true);
        when(modelConfigMapper.selectAiModelHistoryConfigByAiModelConfigIdForUpdate(MODEL_ID))
                .thenReturn(historyModel);
        when(conversationMapper.selectConsumptionByAiConversationIdAndUserId(
                USER_A, CONVERSATION_A)).thenReturn(10L);
        when(conversationMapper.selectBillingByAiConversationIdAndUserId(
                USER_A, CONVERSATION_A)).thenReturn(BigDecimal.ONE);
        when(conversationMapper.updateConversationByAiConversationIdAndUserId(
                org.mockito.ArgumentMatchers.any(AIConversation.class))).thenReturn(1);
    }

    private void assertConversationNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getInfo())
                        .isEqualTo("会话不存在"));
    }
}
