package com.hzmwmt.security;

import com.hzmwmt.domain.controller.dashboard.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAuthorizationContractTest {

    @Test
    void dashboardLivesUnderAdminNamespaceAndRequiresConsoleCapability() {
        RequestMapping mapping = DashboardController.class.getAnnotation(RequestMapping.class);
        PreAuthorize authorization = DashboardController.class.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/admin/dashboard");
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("hasAuthority('admin.console') or hasRole('SUPER_ADMIN')");
    }
}
