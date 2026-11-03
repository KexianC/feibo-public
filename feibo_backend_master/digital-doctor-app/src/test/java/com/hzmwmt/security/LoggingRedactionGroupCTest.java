package com.hzmwmt.security;

import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.approval.CopyWritingApprovalService;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory.ApprovalEvent;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.user.impl.AdminUserServiceImpl;
import com.hzmwmt.domain.user.model.entity.UpdatePasswordCommandEntity;
import com.hzmwmt.infrastructure.gateway.netty.server.UserChannelManager;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LoggingRedactionGroupCTest {

    private static final String PHONE = "13175144211";
    private static final String SECRET =
            "jdbc:mysql://internal/userdb?password=GroupCSecret";
    private static final String TO_STRING_SECRET = "GROUP_C_TO_STRING_SECRET";

    @Test
    void passwordBusinessFailurePreservesExceptionWithoutLoggingPhoneOrDetails(
            CapturedOutput output) {
        AdminUserMapper userMapper = mock(AdminUserMapper.class);
        AuthService authService = mock(AuthService.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper);
        ReflectionTestUtils.setField(service, "authService", authService);
        AppException failure = new AppException("OTP_REJECTED", SECRET);
        when(authService.authUserByVerificationCode(PHONE, "123456")).thenThrow(failure);

        AppException thrown = assertThrows(AppException.class,
                () -> service.updatePassword(UpdatePasswordCommandEntity.builder()
                        .phoneNumber(PHONE)
                        .password("Safe1!Password")
                        .code("123456")
                        .build()));

        assertSame(failure, thrown);
        assertSafeLog(output, PHONE, SECRET);
        assertThat(output.getAll())
                .contains("type=AppException")
                .contains("code=UNMAPPED");
    }

    @Test
    void userCreationFailurePreservesCauseWithoutLoggingThrowableDetails(
            CapturedOutput output) {
        AdminUserMapper userMapper = mock(AdminUserMapper.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        SensitiveRuntimeException failure = new SensitiveRuntimeException(SECRET);
        when(userMapper.insert(any(TUser.class))).thenThrow(failure);
        TUser user = new TUser();
        user.setPhoneNumber(PHONE);
        user.setNickName("new-user");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.createUser(user, null));

        assertThat(thrown.getMessage()).isEqualTo("创建用户失败");
        assertSame(failure, thrown.getCause());
        assertSafeLog(output, PHONE, SECRET);
        assertThat(output.getAll())
                .contains("type=SensitiveRuntimeException")
                .contains("code=" + ResponseCode.SERVICE_ERROR.getCode());
    }

    @Test
    void approvalFailureIsRethrownWithoutLoggingItsMessageOrStack(CapturedOutput output) {
        ICopyWritingRepository repository = mock(ICopyWritingRepository.class);
        @SuppressWarnings("unchecked")
        StateMachineFactory<ApprovalStatusEnumVO, ApprovalEvent> stateMachineFactory =
                mock(StateMachineFactory.class);
        @SuppressWarnings("unchecked")
        StateMachine<ApprovalStatusEnumVO, ApprovalEvent> stateMachine = mock(StateMachine.class);
        CopyWritingApprovalService service = new CopyWritingApprovalService();
        ReflectionTestUtils.setField(service, "copyWritingRepository", repository);
        ReflectionTestUtils.setField(service, "stateMachineFactory", stateMachineFactory);
        when(stateMachineFactory.getStateMachine("808")).thenReturn(stateMachine);
        SensitiveRuntimeException failure = new SensitiveRuntimeException(SECRET);
        when(repository.queryApprovalFlow(808L)).thenThrow(failure);
        OperatedCopywritingApprovalFlowAggregate operation =
                OperatedCopywritingApprovalFlowAggregate.builder()
                        .approvalFlowEntity(CopywritingApprovalFlowEntity.builder()
                                .copywritingApprovalFlowId(808L)
                                .build())
                        .copywritingId(1001L)
                        .operatorUserId(301L)
                        .dataScope(new CopywritingDataScope(301L, false, true, false))
                        .build();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.approvalByExpert(operation));

        assertSame(failure, thrown);
        assertSafeLog(output, SECRET);
        assertThat(output.getAll())
                .contains("type=SensitiveRuntimeException")
                .contains("code=" + ResponseCode.E008.getCode());
    }

    @Test
    void websocketSendFailureReturnsFalseWithoutLoggingThrowableDetails(CapturedOutput output) {
        long userId = 987654321L;
        Channel channel = mock(Channel.class);
        ChannelId channelId = mock(ChannelId.class);
        when(channel.id()).thenReturn(channelId);
        when(channelId.asShortText()).thenReturn("safe-channel-id");
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(true);
        SensitiveRuntimeException failure = new SensitiveRuntimeException(SECRET);
        when(channel.writeAndFlush(any())).thenThrow(failure);

        try {
            UserChannelManager.bind(userId, channel);
            assertFalse(UserChannelManager.sendMessage(userId, "safe-message"));
        } finally {
            UserChannelManager.unbind(channel);
        }

        assertSafeLog(output, SECRET);
        assertThat(output.getAll())
                .contains("type=SensitiveRuntimeException")
                .contains("code=" + ResponseCode.SERVICE_ERROR.getCode());
    }

    @Test
    void groupCSourcesContainNoKnownSensitiveLoggingSinks() throws IOException {
        Path root = findBackendRoot();
        String adminUser = source(root,
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/user/impl/AdminUserServiceImpl.java");
        String baichuan = source(root,
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/ai/service/chat/model/strategy/BaichuanModelStrategyService.java");
        String approval = source(root,
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/copywriting/service/approval/AbstractCopyWritingApprovalService.java");
        String adminCopywriting = source(root,
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/function/impl/AdminCopywritingServiceImpl.java");
        String nettyServer = source(root,
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/netty/server/NettyWebSocketServer.java");
        String channelManager = source(root,
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/netty/server/UserChannelManager.java");
        String tencent = source(root,
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/ai/tencent/TencentAgentChatServiceImpl.java");

        assertThat(adminUser)
                .doesNotContain("log.error(\"创建用户失败: {}\", e.getMessage(), e)")
                .doesNotContain("手机号: {}", "e.getMessage()",
                        "密码修改系统异常\", phoneNumber, e");
        assertThat(baichuan)
                .doesNotContain("log.error(\"百川API调用失败\", error)");
        assertThat(approval)
                .doesNotContain("提交文案审批: {}", "委派给助手，委派命令: {}",
                        "专家认领审批任务: {}", "所属医院/机构管理员认领终审任务: {}",
                        "e.getMessage()");
        assertThat(adminCopywriting)
                .doesNotContain("copywriting: {}", "标题:{}", "昵称: {}",
                        "userName: {}, categoryName: {}",
                        "查询当前操作人信息失败，processorId: {}\", flow.getCurrentProcessorId(), e");
        assertThat(nettyServer)
                .doesNotContain("log.error(\"Netty WebSocket server failed to start\", e)");
        assertThat(channelManager)
                .doesNotContain("e.getMessage()", "unbindEx.getMessage()",
                        "e.getMessage(), e", "unbindEx.getMessage(), unbindEx")
                .contains("消息长度: {}");
        assertThat(tencent)
                .doesNotContain("SessionId:", "payload.getSessionId()", "getMessage()",
                        "异常类型: {}", "WebClient SSE 连接失败 -")
                .contains("解析 SSE 事件失败: code={}, type={}");
    }

    private static void assertSafeLog(CapturedOutput output, String... sensitiveValues) {
        String logs = output.getAll();
        for (String value : sensitiveValues) {
            assertThat(logs).doesNotContain(value);
        }
        assertThat(logs)
                .doesNotContain(TO_STRING_SECRET)
                .doesNotContain("\tat ");
    }

    private static String source(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
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

    private static final class SensitiveRuntimeException extends RuntimeException {
        private SensitiveRuntimeException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return TO_STRING_SECRET;
        }
    }
}
