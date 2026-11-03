package com.hzmwmt.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRbacMigrationContractTest {

    @Test
    void schemaHasUniqueCatalogKeysUniqueMappingAndBothForeignKeys() throws IOException {
        String sql = read("capability_rbac_10_apply_schema.sql").toLowerCase(Locale.ROOT);

        assertThat(sql).contains("unique key uk_t_permission_permission_id (permission_id)");
        assertThat(sql).contains("unique key uk_t_permission_code (code)");
        assertThat(sql).contains("unique key uk_t_role_permission (role_id, permission_id)");
        assertThat(sql).contains("foreign key (role_id) references t_role (role_id)");
        assertThat(sql).contains("foreign key (permission_id) references t_permission (permission_id)");
        assertThat(sql).contains("on update restrict on delete restrict");
    }

    @Test
    void seedKeepsAdminAwayFromSensitiveCapabilitiesAndSuperGetsAll() throws IOException {
        String sql = read("capability_rbac_20_seed.sql").toLowerCase(Locale.ROOT);

        assertThat(sql).contains("'super_admin', 'superadmin'");
        assertThat(sql).contains("'admin.console', 'user.read', 'content.manage'");
        assertThat(sql).contains("'identity.manage'");
        assertThat(sql).contains("'audit.read'");
        assertThat(sql).contains("'system.manage'");
        assertThat(sql).contains("'finance.manage'");
        assertThat(sql).contains("'user.manage'");
        assertThat(sql).contains("declare exit handler for sqlexception");
        assertThat(sql).contains("rollback;");
    }

    @Test
    void rollbackIsConfirmationGatedAndRefusesExtendedCatalog() throws IOException {
        String sql = read("capability_rbac_90_rollback.sql").toLowerCase(Locale.ROOT);

        assertThat(sql).contains("@capability_rbac_rollback_confirmation");
        assertThat(sql).contains("rollback confirmation is missing");
        assertThat(sql).contains("v_permissions <> 8");
        assertThat(sql).contains("automatic v1 rollback refused");
    }

    private static String read(String fileName) throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("docs").resolve("sql").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Cannot locate docs/sql/" + fileName);
    }
}
