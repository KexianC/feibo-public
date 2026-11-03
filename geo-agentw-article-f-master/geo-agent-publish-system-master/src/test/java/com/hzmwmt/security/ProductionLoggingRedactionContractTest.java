package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionLoggingRedactionContractTest {

    private static final List<String> PRODUCTION_LOG_SOURCES = List.of(
            "com/hzmwmt/common/handler/GlobalExceptionHandler.java",
            "com/hzmwmt/security/AuditLogFilter.java",
            "com/hzmwmt/security/LoginAttemptLimiter.java",
            "com/hzmwmt/service/impl/LoginServiceImpl.java",
            "com/hzmwmt/service/impl/KnowledgeDocumentServiceImpl.java",
            "com/hzmwmt/job/KnowledgeDocumentScanJob.java",
            "com/hzmwmt/ai/rag/service/impl/RagKnowledgeBaseServiceImpl.java",
            "com/hzmwmt/ai/rag/service/impl/RagChatServiceImpl.java",
            "com/hzmwmt/ai/rag/service/impl/QdrantRestClientImpl.java",
            "com/hzmwmt/geo/job/GeoMonitorJob.java",
            "com/hzmwmt/geo/service/GeoDialogueProbeWorker.java",
            "com/hzmwmt/geo/service/GeoMonitorWorker.java"
    );

    private static final Pattern LOG_CALL = Pattern.compile(
            "log\\.(?:trace|debug|info|warn|error)\\((?s:.*?)\\);"
    );
    private static final Pattern RAW_THROWABLE_ARGUMENT = Pattern.compile(
            "(?:,|\\()\\s*(?:e|ex|error|exception|throwable)\\s*(?:,|\\))",
            Pattern.DOTALL
    );

    @Test
    void productionLogsExcludeSensitivePayloadsAndRawThrowables() throws IOException {
        Path sourceRoot = resolveSourceRoot();

        for (String relativePath : PRODUCTION_LOG_SOURCES) {
            Path sourcePath = sourceRoot.resolve(relativePath);
            assertThat(sourcePath).as("production logging source exists: %s", relativePath).isRegularFile();
            String source = Files.readString(sourcePath);
            assertThat(source)
                    .as("direct console output in %s", relativePath)
                    .doesNotContain("System.out.", "System.err.", ".printStackTrace(");
            Matcher matcher = LOG_CALL.matcher(source);
            assertThat(matcher.find()).as("expected log calls in %s", relativePath).isTrue();
            do {
                String logCall = matcher.group();
                assertThat(logCall)
                        .as("sensitive log payload in %s", relativePath)
                        .doesNotContain(
                                "getMessage()",
                                "getRequestURI()",
                                "getRemoteAddr()",
                                "getUsername()",
                                "getRole()",
                                "getFilePath()",
                                "getResponseBodyAsString()",
                                "path={}",
                                "remoteAddr",
                                "username={}",
                                "role={}",
                                "title={}",
                                "body={}",
                                "reason={}",
                                "collection={}",
                                "engine={}"
                        );
                assertThat(RAW_THROWABLE_ARGUMENT.matcher(logCall).find())
                        .as("raw throwable argument in %s", relativePath)
                        .isFalse();
            } while (matcher.find());
        }
    }

    private Path resolveSourceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getParent()) {
            Path moduleSource = current.resolve(Path.of("src", "main", "java"));
            if (Files.isRegularFile(moduleSource.resolve(PRODUCTION_LOG_SOURCES.get(0)))) {
                return moduleSource;
            }
            Path workspaceSource = current.resolve(Path.of(
                    "geo-agentw-article-f-master",
                    "geo-agent-publish-system-master",
                    "src",
                    "main",
                    "java"
            ));
            if (Files.isRegularFile(workspaceSource.resolve(PRODUCTION_LOG_SOURCES.get(0)))) {
                return workspaceSource;
            }
        }
        throw new IllegalStateException("Unable to locate GEO Java sources");
    }
}
