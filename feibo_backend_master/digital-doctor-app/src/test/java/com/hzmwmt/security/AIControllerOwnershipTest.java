package com.hzmwmt.security;

import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.service.IChatService;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.trigger.http.client.AIController;
import com.hzmwmt.types.common.CursorPageResponseData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIControllerOwnershipTest {

    private static final Long AUTHENTICATED_USER = 2002L;
    private static final Long REQUESTED_CONVERSATION = 3003L;

    @Mock
    private IChatService chatService;
    @Mock
    private IAuthService authService;

    private AIController controller;

    @BeforeEach
    void setUp() {
        controller = new AIController();
        ReflectionTestUtils.setField(controller, "chatService", chatService);
        ReflectionTestUtils.setField(controller, "authService", authService);
    }

    @Test
    void messageReadCarriesAuthenticatedUserIntoDomainQuery() {
        when(authService.queryValidatedUser()).thenReturn(AuthUserEntity.builder()
                .userId(AUTHENTICATED_USER)
                .build());
        when(chatService.queryAiMessageIndex(AUTHENTICATED_USER, REQUESTED_CONVERSATION, null, 20))
                .thenReturn(CursorPageResponseData.<AIMessageEntity>builder()
                        .data(null)
                        .hasNext(false)
                        .build());

        controller.queryMessages(REQUESTED_CONVERSATION, null, 20);

        verify(chatService).queryAiMessageIndex(AUTHENTICATED_USER, REQUESTED_CONVERSATION, null, 20);
    }
}
