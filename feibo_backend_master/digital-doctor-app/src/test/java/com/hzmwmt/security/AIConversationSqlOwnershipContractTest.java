package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AIConversationSqlOwnershipContractTest {

    @Test
    void everyConversationMessageAndBillingStatementScopesByUserId() throws IOException {
        String conversationXml = classpathResource("/mybatis/mapper/AIConversationMapper.xml");
        String messageXml = classpathResource("/mybatis/mapper/AIMessageMapper.xml");

        for (String statementId : List.of(
                "updateConversationByAiConversationIdAndUserId",
                "validateAiConversationIdAndUserId",
                "selectConsumptionByAiConversationIdAndUserId",
                "selectBillingByAiConversationIdAndUserId"
        )) {
            assertOwnerScoped(statement(conversationXml, statementId), statementId);
        }

        for (String statementId : List.of(
                "selectAiMessageListCursorPageByConversationIdAndUserId",
                "selectAiMessageHistoryByConversationIdAndUserIdAndAiModelConfigId"
        )) {
            String sql = statement(messageXml, statementId);
            assertOwnerScoped(sql, statementId);
            assertThat(sql).as(statementId + " must join the owning conversation")
                    .contains("INNER JOIN t_ai_conversation");
        }
    }

    @Test
    void titleAndDeleteRepositoryUpdatesIncludeConversationAndUserPredicates() throws IOException {
        Path repository = findBackendRoot().resolve(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/AIRepository.java"
        );
        String source = Files.readString(repository);

        for (String methodName : List.of("updateAiConversationTitle", "deleteAiConversation")) {
            String method = javaMethod(source, methodName);
            assertThat(method)
                    .contains(".eq(AIConversation::getAiConversationId")
                    .contains(".eq(AIConversation::getUserId");
        }
    }

    private void assertOwnerScoped(String sql, String statementId) {
        assertThat(sql).as(statementId + " must constrain the conversation id")
                .contains("ai_conversation_id");
        assertThat(sql).as(statementId + " must constrain the authenticated owner")
                .contains("user_id = #{userId}");
    }

    private String statement(String xml, String id) {
        Pattern pattern = Pattern.compile(
                "<(?:select|update)\\s+id=\"" + Pattern.quote(id) + "\"[^>]*>(.*?)</(?:select|update)>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(xml);
        assertThat(matcher.find()).as("mapper statement " + id + " must exist").isTrue();
        return matcher.group(1).replaceAll("\\s+", " ");
    }

    private String javaMethod(String source, String methodName) {
        int start = source.indexOf(" " + methodName + "(");
        assertThat(start).as("repository method " + methodName + " must exist").isGreaterThanOrEqualTo(0);
        int nextOverride = source.indexOf("\n    @Override", start + 1);
        return source.substring(start, nextOverride < 0 ? source.length() : nextOverride);
    }

    private String classpathResource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(name)) {
            assertThat(stream).as(name + " must be on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path findBackendRoot() {
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
