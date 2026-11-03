package com.hzmwmt.security;

import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.controller.function.VideoController;
import com.hzmwmt.domain.controller.user.AdminUserController;
import com.hzmwmt.domain.dto.UserCreateRequest;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.trigger.http.client.MessageController;
import com.hzmwmt.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerExceptionDisclosureSecurityTest {

    private static final String SENSITIVE_PERSISTENCE_ERROR =
            "SQLIntegrityConstraintViolationException: jdbc:mysql://db/internal?password=SuperSecret";
    private static final Pattern RAW_EXCEPTION_RESPONSE = Pattern.compile(
            "(?s)(?:\\breturn\\b|\\.info\\s*\\(|\\.body\\s*\\(|\\.data\\s*\\(|"
                    + "sendSseEvent\\s*\\(|Result\\.error\\s*\\(|Response\\.fail\\s*\\()"
                    + "[^;]{0,800}\\b(?:e|ex|exception)\\s*\\.\\s*"
                    + "(?:getMessage|getLocalizedMessage|getStackTrace|toString)\\s*\\("
    );
    private static final Pattern RAW_EXCEPTION_LOG = Pattern.compile(
            "(?s)log\\.(?:trace|debug|info|warn|error)\\s*\\([^;]{0,800}(?:"
                    + "\\b(?:e|ex|error|exception|throwable|cause|t)\\s*\\.\\s*"
                    + "(?:getMessage|getLocalizedMessage|getStackTrace|toString)\\s*\\("
                    + "|,\\s*(?:e|ex|error|exception|throwable|cause|t)\\s*\\)\\s*;)"
    );
    private static final Pattern SENSITIVE_LOG_ARGUMENT = Pattern.compile(
            "(?s)log\\.(?:trace|debug|info|warn|error)\\s*\\([^;]{0,800}(?:"
                    + "getOriginalFilename\\s*\\(|\\b(?:videoUrl|ossUrl|statusDesc)\\b"
                    + "|filters\\s*:\\s*\\{\\}|新标题\\s*[:：]\\s*\\{\\}"
                    + "|标题(?:搜索关键词)?\\s*[:：]\\s*\\{\\}|第三方API返回数据异常\\s*:\\s*\\{\\})"
    );

    @Test
    void adminUserCreationDoesNotExposePersistenceOrPasswordDetails() {
        AdminUserService userService = mock(AdminUserService.class);
        AdminUserController controller = new AdminUserController();
        ReflectionTestUtils.setField(controller, "adminUserService", userService);
        when(userService.createUser(any(TUser.class), isNull()))
                .thenThrow(new RuntimeException(SENSITIVE_PERSISTENCE_ERROR));

        Result<TUser> result = controller.createUser(validUserRequest());

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("新增用户失败，请稍后重试");
        assertThat(result.getMsg())
                .doesNotContain("SQL")
                .doesNotContain("jdbc:mysql")
                .doesNotContain("SuperSecret");
    }

    @Test
    void legitimateMalformedJsonKeepsStableBusinessPromptWithoutParserDetails() {
        VideoController controller = new VideoController();
        MultipartFile file = mock(MultipartFile.class);

        Result<?> result = controller.uploadCookie(file, "{\"password\":\"SuperSecret\",");

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("JSON格式错误，请检查请求参数");
        assertThat(result.getMsg()).doesNotContain("SuperSecret");
    }

    @Test
    void unauthenticatedMessageQueryDoesNotEchoAuthenticationException() {
        IAuthService authService = mock(IAuthService.class);
        MessageController controller = new MessageController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        when(authService.queryValidatedUser()).thenThrow(new IllegalStateException(SENSITIVE_PERSISTENCE_ERROR));

        Response<?> result = controller.queryNotifyMessages(null, 10);

        assertThat(result.getCode()).isEqualTo(ResponseCode.E0001.getCode());
        assertThat(result.getInfo()).isEqualTo(ResponseCode.E0001.getInfo());
        assertThat(result.getInfo()).doesNotContain("SuperSecret");
    }

    @Test
    void successfulAdminUserCreationRemainsUnchanged() {
        AdminUserService userService = mock(AdminUserService.class);
        AdminUserController controller = new AdminUserController();
        ReflectionTestUtils.setField(controller, "adminUserService", userService);
        TUser createdUser = new TUser();
        createdUser.setUserId(1001L);
        createdUser.setNickName("安全测试用户");
        when(userService.createUser(any(TUser.class), isNull())).thenReturn(createdUser);

        Result<TUser> result = controller.createUser(validUserRequest());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(createdUser);
    }

    @Test
    void noHttpControllerBuildsAResponseFromRawExceptionText() throws IOException {
        Path backendRoot = findBackendRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(backendRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> path.toString().contains("src\\main\\java")
                            || path.toString().contains("src/main/java"))
                    .forEach(path -> inspectController(backendRoot, path, violations));
        }

        assertThat(violations)
                .as("HTTP controllers must not return exception messages, toString values, or stack details")
                .isEmpty();
    }

    @Test
    void noHttpControllerLogsRawExceptionsOrSensitiveRequestValues() throws IOException {
        Path backendRoot = findBackendRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(backendRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> path.toString().contains("src\\main\\java")
                            || path.toString().contains("src/main/java"))
                    .forEach(path -> inspectControllerLogs(backendRoot, path, violations));
        }

        assertThat(violations)
                .as("HTTP controllers must not log raw exceptions, user text, private URLs, or upload filenames")
                .isEmpty();
    }

    private static UserCreateRequest validUserRequest() {
        UserCreateRequest request = new UserCreateRequest();
        request.setNickName("安全测试用户");
        request.setPhoneNumber("13800138000");
        return request;
    }

    private static Path findBackendRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            for (Path candidate : List.of(current, current.resolve("feibo_backend_master"))) {
                if (Files.isDirectory(candidate.resolve("digital-doctor-domain/src/main/java"))
                        && Files.isDirectory(candidate.resolve("digital-doctor-trigger/src/main/java"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("Cannot locate feibo_backend_master source root");
    }

    private static void inspectController(Path backendRoot, Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            Matcher matcher = RAW_EXCEPTION_RESPONSE.matcher(source);
            if (matcher.find()) {
                violations.add(backendRoot.relativize(path).toString());
            }
        } catch (IOException e) {
            throw new AssertionError("Cannot inspect controller source: " + path, e);
        }
    }

    private static void inspectControllerLogs(Path backendRoot, Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (RAW_EXCEPTION_LOG.matcher(source).find() || SENSITIVE_LOG_ARGUMENT.matcher(source).find()) {
                violations.add(backendRoot.relativize(path).toString());
            }
        } catch (IOException e) {
            throw new AssertionError("Cannot inspect controller log source: " + path, e);
        }
    }
}
