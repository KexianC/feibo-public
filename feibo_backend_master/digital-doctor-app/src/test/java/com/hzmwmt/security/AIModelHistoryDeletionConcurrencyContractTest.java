package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AIModelHistoryDeletionConcurrencyContractTest {

    @Test
    void everyModelEntryAndManagementQueryExcludesTombstones() throws Exception {
        Path backendRoot = findBackendRoot();
        String runtimeMapperXml = Files.readString(backendRoot.resolve(
                "digital-doctor-app/src/main/resources/mybatis/mapper/AIModelConfigMapper.xml"));
        for (String statement : new String[]{
                "selectAllAiModelConfig",
                "selectAiModelLeafConfigByAiModelConfigId",
                "selectAiModelRuntimeConfigByAiModelConfigId"}) {
            assertThat(xmlStatement(runtimeMapperXml, statement))
                    .as(statement)
                    .contains("is_deleted = false");
        }

        String adminMapperSource = Files.readString(backendRoot.resolve(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));
        for (String statement : new String[]{
                "selectManagedModels",
                "selectProviderTemplate",
                "countManagedProviderScopesForModelCode",
                "selectManagedModelById",
                "lockManagedGeneralRootForUpdate",
                "lockManagedProviderScopeForUpdate",
                "selectManagedProvidersForUpdate",
                "countManagedEmptyProviders",
                "selectManagedSiblingModelsForUpdate",
                "updateManagedProviderOrder",
                "updateManagedModelOrder",
                "updateManagedLeafMetadata",
                "updateManagedLeafVisibility",
                "updateManagedLeafApiKey"}) {
            assertThat(mapperStatement(adminMapperSource, statement))
                    .as(statement)
                    .contains("is_deleted = FALSE");
        }

        String serviceSource = Files.readString(backendRoot.resolve(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/service/user/impl/AdminModelConfigServiceImpl.java"));
        assertThat(serviceSource.split(
                "\\.eq\\(TAiModelConfig::getIsDeleted, false\\)", -1))
                .hasSizeGreaterThanOrEqualTo(3);

        String registrySource = Files.readString(backendRoot.resolve(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/ai/DatabaseBackedChatModelRegistry.java"));
        assertThat(registrySource).contains(
                "Boolean.TRUE.equals(config.getIsDeleted())");
    }

    @Test
    void chatHistorySaveAndAdministrativeDeleteShareTheModelRowLockProtocol()
            throws Exception {
        Path backendRoot = findBackendRoot();
        String runtimeMapperXml = Files.readString(backendRoot.resolve(
                "digital-doctor-app/src/main/resources/mybatis/mapper/AIModelConfigMapper.xml"));
        String lockingRead = xmlStatement(
                runtimeMapperXml,
                "selectAiModelHistoryConfigByAiModelConfigIdForUpdate");

        assertThat(lockingRead)
                .contains("from t_ai_model_config")
                .contains("ai_model_config_id = #{aiModelConfigId}")
                .contains("is_leaf = true")
                .contains("for update")
                .doesNotContain("visible = true", "is_deleted = false");

        String runtimeRead = xmlStatement(
                runtimeMapperXml,
                "selectAiModelRuntimeConfigByAiModelConfigId");
        String publicLeafRead = xmlStatement(
                runtimeMapperXml,
                "selectAiModelLeafConfigByAiModelConfigId");
        assertThat(runtimeRead)
                .contains("visible = true", "is_deleted = false");
        assertThat(publicLeafRead)
                .contains("visible = true", "is_deleted = false");

        String repositorySource = Files.readString(backendRoot.resolve(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/adapter/repository/AIRepository.java"));
        String saveMethod = javaMethod(repositorySource, "public Long saveChatHistory(");
        assertBefore(saveMethod,
                "selectAiModelHistoryConfigByAiModelConfigIdForUpdate",
                "selectConsumptionByAiConversationIdAndUserId");
        assertBefore(saveMethod,
                "selectAiModelHistoryConfigByAiModelConfigIdForUpdate",
                "updateConversationByAiConversationIdAndUserId");
        assertBefore(saveMethod,
                "selectAiModelHistoryConfigByAiModelConfigIdForUpdate",
                "aiMessageMapper.insert");
        assertThat(saveMethod).doesNotContain("queryAiModelLeafConfig(");

        String adminMapperSource = Files.readString(backendRoot.resolve(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));
        String siblingLock = mapperStatement(
                adminMapperSource, "selectManagedSiblingModelsForUpdate");
        String softDelete = mapperStatement(
                adminMapperSource, "softDeleteManagedLeafModel");
        assertThat(siblingLock)
                .contains("FROM t_ai_model_config leaf")
                .contains("leaf.is_leaf = TRUE")
                .contains("leaf.is_deleted = FALSE")
                .contains("FOR UPDATE");
        assertThat(softDelete)
                .contains("UPDATE t_ai_model_config leaf")
                .contains("leaf.visible = FALSE")
                .contains("leaf.is_deleted = TRUE")
                .contains("leaf.api_key_ciphertext = NULL")
                .contains("leaf.is_deleted = FALSE")
                .doesNotContain("DELETE", "t_ai_message");
        assertThat(adminMapperSource).doesNotContain(
                "countAiMessageReferences", "deleteManagedLeafModel");
    }

    @Test
    void messageModelReferenceIndexMigrationIsIdempotentAndDataPreserving()
            throws Exception {
        String migration = Files.readString(findBackendRoot().resolve(
                "docs/sql/add_ai_message_model_config_index.sql"));

        assertThat(migration)
                .contains("information_schema.STATISTICS")
                .contains("COLUMN_NAME = 'ai_model_config_id'")
                .contains("SEQ_IN_INDEX = 1")
                .contains("@compatible_index_count > 0")
                .contains("ALTER TABLE t_ai_message ADD INDEX idx_ai_message_model_config_id")
                .contains("ALGORITHM=INPLACE")
                .contains("LOCK=NONE")
                .contains("@compatible_index_after > 0")
                .contains("__ai_message_model_index_preflight_failed__")
                .contains("__ai_message_model_index_verification_failed__");
        assertThat(migration).doesNotContain(
                "DELETE FROM t_ai_message",
                "UPDATE t_ai_message",
                "CREATE INDEX",
                "FOREIGN KEY",
                "ADD CONSTRAINT",
                "deletion guard");
    }

    @Test
    void modelTombstoneSchemaMigrationIsIdempotentOnlineAndFailClosed()
            throws Exception {
        String migration = Files.readString(findBackendRoot().resolve(
                "docs/sql/add_ai_model_soft_delete.sql"));

        assertThat(migration)
                .contains("information_schema.COLUMNS")
                .contains("COLUMN_NAME = 'is_deleted'")
                .contains("is_deleted TINYINT(1) NOT NULL DEFAULT 0")
                .contains("ALGORITHM=INSTANT")
                .contains("@deleted_column_count = 1")
                .contains("@deleted_null_count = 0")
                .contains("MySQL 8.0.36")
                .contains("before deploying application")
                .contains("without --force")
                .contains("__ai_model_soft_delete_preflight_failed__")
                .contains("__ai_model_soft_delete_verification_failed__");
        assertThat(migration).doesNotContain(
                "UPDATE t_ai_model_config",
                "DELETE FROM t_ai_model_config",
                "ALGORITHM=COPY");
    }

    private void assertBefore(String source, String first, String second) {
        assertThat(source.indexOf(first)).as(first + " must be present")
                .isGreaterThanOrEqualTo(0);
        assertThat(source.indexOf(second)).as(second + " must be present")
                .isGreaterThanOrEqualTo(0);
        assertThat(source.indexOf(first)).isLessThan(source.indexOf(second));
    }

    private String xmlStatement(String xml, String id) {
        Pattern pattern = Pattern.compile(
                "<select\\s+id=\"" + Pattern.quote(id)
                        + "\"[^>]*>(.*?)</select>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        assertThat(matcher.find()).as("mapper statement " + id + " must exist")
                .isTrue();
        return matcher.group(1).replaceAll("\\s+", " ");
    }

    private String javaMethod(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as("Java method must exist: " + signature)
                .isGreaterThanOrEqualTo(0);
        int end = source.indexOf("\n    @Override", start + signature.length());
        assertThat(end).as("Java method boundary must exist: " + signature)
                .isGreaterThan(start);
        return source.substring(start, end);
    }

    private String mapperStatement(String mapperSource, String methodName) {
        int methodIndex = mapperSource.indexOf(methodName + "(");
        assertThat(methodIndex).as("Mapper method must exist: " + methodName)
                .isGreaterThanOrEqualTo(0);
        int annotationIndex = Math.max(
                mapperSource.lastIndexOf("@Select", methodIndex),
                Math.max(
                        mapperSource.lastIndexOf("@Update", methodIndex),
                        mapperSource.lastIndexOf("@Delete", methodIndex)));
        int statementEnd = mapperSource.indexOf(';', methodIndex);
        assertThat(annotationIndex).isGreaterThanOrEqualTo(0);
        assertThat(statementEnd).isGreaterThan(methodIndex);
        return mapperSource.substring(annotationIndex, statementEnd + 1);
    }

    private Path findBackendRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8;
                depth++, current = current.getParent()) {
            for (Path candidate : new Path[]{
                    current,
                    current.resolve("feibo_backend_master")}) {
                if (Files.isDirectory(candidate.resolve(
                        "digital-doctor-app/src/main/resources/mybatis/mapper"))
                        && Files.isDirectory(candidate.resolve(
                        "digital-doctor-infrastructure/src/main/java"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("Cannot locate feibo_backend_master root");
    }
}
