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

class ProductionLoggingResidualRedactionContractTest {

    private static final List<String> SOURCE_FILES = List.of(
            "feibo_backend_master/digital-doctor-types/src/main/java/com/hzmwmt/types/util/FileUploadUtil.java",
            "feibo_backend_master/digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/function/impl/AdminVideoServiceImpl.java",
            "feibo_backend_master/digital-doctor-trigger/src/main/java/com/hzmwmt/trigger/http/client/DigitalHumanController.java",
            "geo-agentw-article-f-master/geo-agent-publish-system-master/src/main/java/com/hzmwmt/ai/rag/document/DocumentParseStrategyFactory.java",
            "feibo_backend_master/digital-doctor-app/src/main/java/com/hzmwmt/aop/OperationLogAspect.java",
            "feibo_backend_master/digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/port/TradePort.java",
            "feibo_backend_master/digital-doctor-domain/src/main/java/com/hzmwmt/domain/trade/service/settlement/trial/node/FailureNode.java"
    );

    private static final Pattern LOG_CALL = Pattern.compile(
            "(?s)\\b(?:log|logger)\\.(?:trace|debug|info|warn|error)\\s*\\(.*?\\);"
    );
    private static final Pattern RAW_THROWABLE_ARGUMENT = Pattern.compile(
            "(?s)(?:,|\\()\\s*(?:e|ex|error|exception|throwable|releaseError)\\s*(?:,|\\))"
    );
    private static final Pattern BARE_SENSITIVE_ARGUMENT = Pattern.compile(
            "(?s)(?:,|\\()\\s*(?:filename|region|line|errorMsg)\\s*(?:,|\\))"
    );

    @Test
    void productionReachableLogsContainOnlySafeDiagnosticMetadata() throws IOException {
        Path workspaceRoot = findWorkspaceRoot();
        List<String> inspected = new ArrayList<>();

        for (String relativePath : SOURCE_FILES) {
            Path sourcePath = workspaceRoot.resolve(relativePath);
            assertThat(sourcePath).as("production logging source exists: %s", relativePath).isRegularFile();
            String source = scopedSource(relativePath, Files.readString(sourcePath));

            assertThat(source)
                    .as("production source avoids direct console and stack output: %s", relativePath)
                    .doesNotContain("System.out.", "System.err.", ".printStackTrace(");

            List<String> logCalls = extractLogCalls(source);
            assertThat(logCalls).as("production source has log calls: %s", relativePath).isNotEmpty();
            for (String logCall : logCalls) {
                assertThat(logCall)
                        .as("log call contains safe metadata only in %s", relativePath)
                        .doesNotContain(
                                ".getMessage(",
                                ".getLocalizedMessage(",
                                ".getOriginalFilename(",
                                ".getAbsolutePath(",
                                ".getTitle(",
                                ".getErrMsg(",
                                ".getRetMsg(",
                                "getResponseBodyAsString(",
                                "metadata.get(",
                                "tempFilePath",
                                "ffmpegPath",
                                "filePath",
                                "nullOutput",
                                "errorMsg");
                assertThat(RAW_THROWABLE_ARGUMENT.matcher(logCall).find())
                        .as("log call does not persist a raw throwable in %s: %s", relativePath, logCall)
                        .isFalse();
                assertThat(BARE_SENSITIVE_ARGUMENT.matcher(logCall).find())
                        .as("log call does not persist raw filename, region, process output, or payment text in %s: %s",
                                relativePath, logCall)
                        .isFalse();
            }
            inspected.add(relativePath);
        }

        assertThat(inspected).containsExactlyElementsOf(SOURCE_FILES);
        assertThat(inspected).hasSize(7).doesNotHaveDuplicates();
    }

    private static String scopedSource(String relativePath, String source) {
        if (!relativePath.endsWith("FileUploadUtil.java")) {
            return source;
        }
        int start = source.indexOf("public static int getAudioDurationWithTika");
        int end = source.indexOf("public static AudioInfoUtil.AudioInfo getAudioFormatAndSampleRate", start);
        assertThat(start).as("reachable FileUploadUtil logging scope starts").isGreaterThanOrEqualTo(0);
        assertThat(end).as("reachable FileUploadUtil logging scope ends").isGreaterThan(start);
        return source.substring(start, end);
    }

    private static List<String> extractLogCalls(String source) {
        List<String> logCalls = new ArrayList<>();
        Matcher matcher = LOG_CALL.matcher(source);
        while (matcher.find()) {
            logCalls.add(matcher.group());
        }
        return logCalls;
    }

    private static Path findWorkspaceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("feibo_backend_master/digital-doctor-domain/src/main/java"))
                    && Files.isDirectory(current.resolve(
                    "geo-agentw-article-f-master/geo-agent-publish-system-master/src/main/java"))) {
                return current;
            }
        }
        throw new AssertionError("Cannot locate workspace source root");
    }
}
