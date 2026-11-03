package com.hzmwmt.domain.ai.service.chat;

import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiStreamErrorDisclosureSecurityTest {

    private static final String SENSITIVE_DOWNSTREAM_ERROR =
            "SQLState=28000 jdbc:mysql://internal?password=SuperSecret";

    @Test
    void validationFailureDoesNotExposeUnexpectedExceptionText() {
        ChatService service = new ChatService();
        service.aiChatValidationFactory = mock(AIChatValidationFactory.class);
        when(service.aiChatValidationFactory.openLogicFilter())
                .thenThrow(new IllegalStateException(SENSITIVE_DOWNSTREAM_ERROR));

        RuleLogicEntity<AIChatRuleCommandAggregate> result = service.doCheckLogic(
                AIChatRuleCommandAggregate.builder().build(),
                AIChatValidationFactory.LogicModel.PARAMETER_VALIDATION.getCode());

        assertThat(result.getInfo()).isEqualTo(ResponseCode.SERVICE_ERROR.getInfo());
        assertThat(result.getInfo()).doesNotContain("jdbc:mysql", "SuperSecret", "SQLState");
    }

    @Test
    void streamErrorsUseFixedMessagesWhileKnownBusinessErrorsRemainVisible() {
        assertThat(AbstractChatService.clientSafeStreamError(
                new IllegalStateException(SENSITIVE_DOWNSTREAM_ERROR)))
                .isEqualTo(ResponseCode.SERVICE_ERROR.getInfo());
        assertThat(AbstractChatService.clientSafeStreamError(
                new AppException(ResponseCode.INTERNET_EXCEPTION.getCode(), SENSITIVE_DOWNSTREAM_ERROR)))
                .isEqualTo(ResponseCode.INTERNET_EXCEPTION.getInfo());
        assertThat(AbstractChatService.clientSafeStreamError(
                new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), SENSITIVE_DOWNSTREAM_ERROR)))
                .isEqualTo(ResponseCode.REMOTE_REQUEST_EXCEPTION.getInfo());
        assertThat(AbstractChatService.clientSafeStreamError(
                new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), "消息命中敏感内容，发送失败")))
                .isEqualTo("消息命中敏感内容，发送失败");
        assertThat(AbstractChatService.clientSafeStreamError(
                new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), "超出并发数限制")))
                .isEqualTo("超出并发数限制");
    }

    @Test
    void streamingAndTencentGatewayNeverForwardRawThrowableText() throws IOException {
        Path root = findBackendRoot();
        String abstractChat = Files.readString(root.resolve(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/ai/service/chat/AbstractChatService.java"));
        String chatService = Files.readString(root.resolve(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/ai/service/chat/ChatService.java"));
        String tencentGateway = Files.readString(root.resolve(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/ai/tencent/TencentAgentChatServiceImpl.java"));
        String openAiGateway = Files.readString(root.resolve(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/ai/openai/openAIChatService.java"));

        assertThat(abstractChat)
                .doesNotContain("safeSend.accept(\"error_message\", error.getMessage())", "getMessage()")
                .doesNotContain("modelName, error)", "\", e);");
        assertThat(chatService)
                .doesNotContain(".info(e.getMessage())", "\", e);");
        assertThat(tencentGateway)
                .doesNotContain("\"解析 SSE 事件失败: \" + e.getMessage()")
                .doesNotContain("\"SSE 连接失败: \" + error.getMessage()")
                .doesNotContain("\"创建 SSE 连接失败: \" + e.getMessage()")
                .doesNotContain("请求参数: {}", "跳过第一条消息: {}", "[thought] 内容: {}")
                .doesNotContain("getMessage()", "\", e);", "\", error);");
        assertThat(openAiGateway)
                .doesNotContain("flowGenerate finalResponse:{}")
                .contains("finalResponse.length()");
    }

    private static Path findBackendRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            for (Path candidate : List.of(current, current.resolve("feibo_backend_master"))) {
                if (Files.isDirectory(candidate.resolve("digital-doctor-domain/src/main/java"))
                        && Files.isDirectory(candidate.resolve("digital-doctor-infrastructure/src/main/java"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("Cannot locate feibo_backend_master source root");
    }
}
