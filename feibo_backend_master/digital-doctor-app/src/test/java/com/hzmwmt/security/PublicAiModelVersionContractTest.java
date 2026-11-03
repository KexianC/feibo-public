package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAiModelVersionContractTest {

    @Test
    void runtimeDefaultsUseConfirmedCurrentModelIds() throws IOException {
        String aiPort = readFromRepository(
                "digital-doctor-infrastructure/src/main/java/com/hzmwmt/" +
                        "infrastructure/adapter/port/AIPort.java");
        String chatConfiguration = readFromRepository(
                "digital-doctor-app/src/main/java/com/hzmwmt/config/" +
                        "ChatModelConfiguration.java");
        String dev = readFromRepository(
                "digital-doctor-app/src/main/resources/application-dev.yml");
        String prod = readFromRepository(
                "digital-doctor-app/src/main/resources/application-prod.yml");

        assertThat(count(aiPort, ".model(\"qwen3.7-flash\")")).isEqualTo(2);
        assertThat(aiPort).doesNotContain(".model(\"qwen-flash\")");
        assertThat(chatConfiguration).contains(".model(\"deepseek-v4-flash\")");
        assertThat(chatConfiguration).doesNotContain(".model(\"deepseek-chat\")");
        assertThat(dev).contains("model: deepseek-v4-flash");
        assertThat(prod).contains("model: deepseek-v4-flash");
        assertThat(dev).doesNotContain("model: deepseek-chat");
        assertThat(prod).doesNotContain("model: deepseek-chat");
    }

    @Test
    void stableProviderCodesDoNotExposeAStaleGlmVersionLabel() throws IOException {
        String frontendService = readFromRepository(
                "../feibo_backend/src/services/aiModelAdminService.ts");
        String glmStrategy = readFromRepository(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/ai/" +
                        "service/chat/model/strategy/Glm52ModelStrategyService.java");
        String chatConfiguration = readFromRepository(
                "digital-doctor-app/src/main/java/com/hzmwmt/config/" +
                        "ChatModelConfiguration.java");

        assertThat(frontendService).contains(
                "{ value: 'glm52', label: '智谱 GLM' }");
        assertThat(frontendService).doesNotContain("智谱 GLM 5.2");
        assertThat(glmStrategy).contains("Zhipu GLM models");
        assertThat(glmStrategy).doesNotContain("GLM-5.2 uses");
        assertThat(chatConfiguration).contains(
                "The stable bean/configuration key remains {@code glm52}");
    }

    @Test
    void baichuanStrategyAcceptsCurrentM3ModelsWithoutRemovingLegacyCompatibility()
            throws IOException {
        String strategy = readFromRepository(
                "digital-doctor-domain/src/main/java/com/hzmwmt/domain/ai/" +
                        "service/chat/model/strategy/BaichuanModelStrategyService.java");

        assertThat(strategy).contains(
                "\"Baichuan-M3-Plus\"",
                "\"Baichuan-M3\"",
                "\"Baichuan-M2-Plus\"",
                "\"Baichuan-M2\"");
    }

    @Test
    void forwardMigrationIsScopedIdempotentAndFailsClosedBeforeCommit()
            throws IOException {
        String sql = readFromRepository(
                "docs/sql/update_public_ai_model_versions_20260822.sql");

        assertThat(sql).contains(
                "9005200000000000001",
                "'glm-5.3'",
                "1970339281184886785",
                "'doubao-seed-2-1-pro-260628'",
                "1970339281478488066",
                "'qwen3.8-max'",
                "2026505489033986048",
                "'qwen3.7-plus'",
                "2026505489033986049",
                "'qwen3.7-flash'",
                "1970687514398605378",
                "'Baichuan-M3-Plus'");
        assertThat(sql).contains(
                "START TRANSACTION;",
                "FOR UPDATE;",
                "@qwen_original_identity_count = 5",
                "@qwen_legacy_sort_count = 5",
                "@qwen_normalized_old_order_count = 5",
                "@qwen_product_order_count = 5",
                "@target_business_id_count = 8",
                "@active_target_business_row_count = 8",
                "@target_name_count_after = 8",
                "@qwen_target_state_count_after = 5",
                "@stale_default_display_name_count_after = 0",
                "@stale_standard_description_count_after = 0",
                "@protected_fields_changed = 0",
                "__public_model_version_preflight_failed__",
                "__public_model_version_commit_verification_failed__");
        assertThat(sql.indexOf("EXECUTE public_model_preflight_stmt"))
                .isLessThan(sql.indexOf("UPDATE t_ai_model_config AS leaf"));
        assertThat(sql).contains(
                "leaf.is_deleted = 0",
                "provider.is_deleted = 0",
                "root.is_deleted = 0");
        assertThat(sql).contains(
                "leaf.display_name = CASE",
                "BINARY leaf.display_name = BINARY 'glm-5.2' THEN 'glm-5.3'",
                "BINARY leaf.description = BINARY '智谱 GLM-5.2 旗舰模型'",
                "THEN '智谱 GLM-5.3 旗舰模型'",
                "THEN '通义千问 Qwen3.8 Max 旗舰模型'");
        assertThat(sql.lastIndexOf("EXECUTE public_model_commit_stmt"))
                .isLessThan(sql.indexOf("\nCOMMIT;"));
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "DROP TABLE", "TRUNCATE TABLE");
        assertThat(sql).doesNotContain(
                "deepseek-v4", "kimi-k3", "hy3", "api_key_ciphertext =");
    }

    @Test
    void rollbackRestoresTheVerifiedProductionStateAndIsConfirmationGated()
            throws IOException {
        String sql = readFromRepository(
                "docs/sql/rollback_public_ai_model_versions_20260822.sql");

        assertThat(sql).contains(
                "@public_model_version_rollback_confirmation",
                "ROLLBACK_20260822_PUBLIC_MODELS",
                "WHEN 1970339281478488066 THEN 1",
                "WHEN 2026505489033986048 THEN 2",
                "WHEN 2026505489033986049 THEN 3",
                "WHEN 1970339281906307073 THEN 4",
                "WHEN 1970339281788866562 THEN 5",
                "@rollback_name_count_after = 8",
                "@qwen_original_state_count_after = 5",
                "@rollback_target_business_id_count = 8",
                "@rollback_active_target_count = 8",
                "@rollback_valid_scoped_target_count = 8",
                "@rollback_qwen_state_supported = 1",
                "@migration_generated_display_name_count_after = 0",
                "@migration_generated_description_count_after = 0",
                "__public_model_version_rollback_preflight_failed__",
                "__public_model_version_rollback_commit_failed__");
        assertThat(sql.lastIndexOf("EXECUTE public_model_rollback_commit_stmt"))
                .isLessThan(sql.indexOf("\nCOMMIT;"));
        assertThat(sql).contains(
                "leaf.is_deleted = 0",
                "provider.is_deleted = 0",
                "root.is_deleted = 0");
        assertThat(sql).contains(
                "leaf.display_name = CASE",
                "BINARY leaf.display_name = BINARY 'glm-5.3' THEN 'glm-5.2'",
                "BINARY leaf.description = BINARY '智谱 GLM-5.3 旗舰模型'",
                "THEN '智谱 GLM-5.2 旗舰模型'");
        assertThat(sql).doesNotContain(
                "WHEN 1970339281478488066 THEN 10",
                "WHEN 1970339281788866562 THEN 20",
                "WHEN 1970339281906307073 THEN 30",
                "WHEN 2026505489033986048 THEN 40",
                "WHEN 2026505489033986049 THEN 50");
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "DROP TABLE", "TRUNCATE TABLE");
    }

    @Test
    void geoAgentKeepsItsCompatibleLegacyTextProtocolDefault() throws IOException {
        String geo = readFromRepository(
                "digital-doctor-app/src/main/resources/geo-agent-integration.yml");

        assertThat(geo).contains(
                "base-url: ${GEO_QWEN_BASE_URL:https://dashscope.aliyuncs.com/" +
                        "api/v1/services/aigc/text-generation/generation}");
        assertThat(count(geo, "qwen-plus}")).isEqualTo(2);
        assertThat(geo).doesNotContain("qwen3.7-plus");
    }

    private static int count(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }

    private static String readFromRepository(String relativePath) throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path direct = cursor.resolve(relativePath).normalize();
            if (Files.isRegularFile(direct)) {
                return Files.readString(direct);
            }
            Path nested = cursor.resolve("feibo_backend_master")
                    .resolve(relativePath).normalize();
            if (Files.isRegularFile(nested)) {
                return Files.readString(nested);
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Cannot locate repository file: " + relativePath);
    }
}
