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

class LoggingRedactionGroupDContractTest {

    private static final List<String> SOURCE_FILES = List.of(
            "digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/listener/RegisterSuccessListener.java",
            "digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/listener/OrderPaySuccessListener.java",
            "digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/job/NoPayNotifyOrderJob.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/util/ip/IpRegionUtil.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/TradeRepository.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/sms/ALiYunSMSGateway.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/aop/DistributedLockAspect.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/AuthRepository.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/port/UserPort.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/pay/util/AllInPaySecurityUtil.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/pay/AllInPayWebClientService.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/AbstractRepository.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/port/AIPort.java",
            "digital-doctor-app/src/main/java/com/hzmwmt/config/SensitiveWordConfig.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/oss/MinIOGateway.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/oss/AliyunOSSGateway.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/oss/HybridOSSGateway.java",
            "digital-doctor-domain/src/main/java/com/hzmwmt/domain/user/service/manager/UserManagerServiceImpl.java",
            "digital-doctor-api/src/main/java/com/hzmwmt/api/controller/FileController.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/netty/NettyService.java",
            "digital-doctor-domain/src/main/java/com/hzmwmt/domain/copywriting/service/approval/statemachine/ApprovalAction.java",
            "digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/asr/impl/DashScopeASRService.java",
            "digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/user/impl/AdminConversationServiceImpl.java",
            "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/CopyWritingRepository.java",
            "digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/job/CopywritingAiApprovalJob.java"
    );

    private static final Pattern LOG_CALL = Pattern.compile(
            "(?s)\\blog\\.(?:trace|debug|info|warn|error)\\s*\\(.*?\\);"
    );
    private static final Pattern BARE_SENSITIVE_ARGUMENT = Pattern.compile(
            "(?s)(?:,|\\()\\s*(?:e|ex|error|throwable|phoneNum|phoneNumber|content|title|message|jsonMessage|"
                    + "aiResponse|response|stringToSign|stringToVerify|objectName|objectUrl|objectKey|minIOUrl|"
                    + "minioUrl|aliyunUrl|"
                    + "fileUrl|presignedUrl|newAvatarUrl|oldAvatarUrl|newNickName|specialty|ip|region|"
                    + "lockKey|fileName|bucketName|productTypeEnumVO|event|updateFlow|result|schedulingException|"
                    + "dispatchException|queryException|retryException|exception)\\s*(?:,|\\))"
    );

    @Test
    void exactGroupDSourceInventoryContainsNoSensitiveLogSink() throws IOException {
        Path backendRoot = findBackendRoot();
        List<String> inspected = new ArrayList<>();

        for (String relativePath : SOURCE_FILES) {
            Path sourcePath = backendRoot.resolve(relativePath);
            assertThat(sourcePath).as("group D source exists: %s", relativePath).isRegularFile();
            String source = Files.readString(sourcePath);
            List<String> logCalls = extractLogCalls(source);
            if (relativePath.equals("digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/TradeRepository.java")) {
                // The entitlement implementation intentionally has no log sink.
                assertThat(logCalls).as("TradeRepository remains log-free").isEmpty();
            } else {
                assertThat(logCalls).as("group D source has log calls: %s", relativePath).isNotEmpty();
            }

            for (String logCall : logCalls) {
                assertThat(logCall)
                        .as("safe log metadata only in %s", relativePath)
                        .doesNotContain(".getMessage(", ".toString(", "aiResponse",
                                "stringToSign", "stringToVerify");
                assertThat(BARE_SENSITIVE_ARGUMENT.matcher(logCall).find())
                        .as("no throwable, raw content, URL, key, IP or profile value in %s: %s",
                                relativePath, logCall)
                        .isFalse();
            }
            inspected.add(relativePath);
        }

        assertThat(inspected).containsExactlyElementsOf(SOURCE_FILES);
        assertThat(inspected).hasSize(25).doesNotHaveDuplicates();
    }

    private static List<String> extractLogCalls(String source) {
        List<String> logCalls = new ArrayList<>();
        Matcher matcher = LOG_CALL.matcher(source);
        while (matcher.find()) {
            logCalls.add(matcher.group());
        }
        return logCalls;
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
