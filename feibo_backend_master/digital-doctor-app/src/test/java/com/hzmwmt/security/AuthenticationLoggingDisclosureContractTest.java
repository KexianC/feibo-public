package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationLoggingDisclosureContractTest {

    private static final Pattern LOG_CALL = Pattern.compile(
            "(?s)\\blog\\.(?:trace|debug|info|warn|error)\\s*\\([^;]*?\\);"
    );
    private static final Pattern RAW_THROWABLE_ARGUMENT = Pattern.compile(
            "(?s),\\s*(?:e|ex|exception|throwable|t)\\s*\\)\\s*;"
    );
    private static final Pattern RAW_SECRET_ARGUMENT = Pattern.compile(
            "(?s),\\s*(?:bearerToken|token|refreshToken|accessToken|clientFingerprint|jti)\\b"
    );
    private static final List<String> SOURCE_FILES = List.of(
            "digital-doctor-app/src/main/java/com/hzmwmt/aop/IdempotentAspect.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/aop/OperationLogAspect.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/aop/LoginLogAspect.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/config/MybatisPlusConfig.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/security/JwtAuthenticationFilter.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/port/AuthPort.java",
            "digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/http/client/AuthController.java"
    );

    @Test
    void authenticationLogsContainOnlySafeDiagnosticMetadata() throws IOException {
        Path backendRoot = findBackendRoot();
        List<String> violations = new ArrayList<>();

        for (String sourceFile : SOURCE_FILES) {
            String source = Files.readString(backendRoot.resolve(sourceFile));
            Matcher matcher = LOG_CALL.matcher(source);
            while (matcher.find()) {
                String statement = matcher.group();
                if (statement.contains("getMessage(")
                        || statement.contains("getLocalizedMessage(")
                        || statement.contains("printStackTrace(")
                        || statement.contains(".toString(")
                        || statement.contains(".getToken(")
                        || statement.contains("claims.getId(")
                        || RAW_THROWABLE_ARGUMENT.matcher(statement).find()
                        || RAW_SECRET_ARGUMENT.matcher(statement).find()) {
                    violations.add(sourceFile + ": " + statement.replaceAll("\\s+", " "));
                }
            }
        }

        assertThat(violations)
                .as("authentication logs must not persist raw exceptions, tokens, JWT identifiers, or toString values")
                .isEmpty();
    }

    private static Path findBackendRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            for (Path candidate : List.of(current, current.resolve("feibo_backend_master"))) {
                if (Files.isDirectory(candidate.resolve("digital-doctor-app/src/main/java"))
                        && Files.isDirectory(candidate.resolve("digital-doctor-infrastructure/src/main/java"))
                        && Files.isDirectory(candidate.resolve("digital-doctor-trigger/src/main/java"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("Cannot locate feibo_backend_master source root");
    }
}
