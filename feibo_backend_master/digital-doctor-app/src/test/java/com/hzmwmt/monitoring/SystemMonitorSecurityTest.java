package com.hzmwmt.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemMonitorSecurityTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void anonymousCannotStartAnyProbe() {
        try (AnnotationConfigApplicationContext context = context()) {
            assertThatThrownBy(context.getBean(SystemMonitorController.class)::monitor)
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            verifyNoInteractions(context.getBean(SystemMonitorService.class));
        }
    }

    @Test
    void ordinaryAdminAndCapabilityStringDoNotGrantSystemAccess() {
        try (AnnotationConfigApplicationContext context = context()) {
            for (String[] authorities : List.of(new String[]{"ROLE_ADMIN"}, new String[]{"ROLE_ADMIN", "system.manage"})) {
                authenticate(authorities);
                assertThatThrownBy(context.getBean(SystemMonitorController.class)::monitor)
                        .isInstanceOf(AccessDeniedException.class);
            }
            verifyNoInteractions(context.getBean(SystemMonitorService.class));
        }
    }

    @Test
    void superAdminGetsRealServiceSnapshotThroughSecuredHttpHandlerWithoutClientCaching() throws Exception {
        try (AnnotationConfigApplicationContext context = context()) {
            SystemMonitorSnapshot snapshot = new SystemMonitorSnapshot("2026-09-14T00:00:00Z", 15,
                    List.of(new MonitorReadings("database").unavailable("DOWN", "数据库连接或只读探测失败").build()));
            SystemMonitorService service = context.getBean(SystemMonitorService.class);
            when(service.snapshot()).thenReturn(snapshot);
            authenticate("ROLE_SUPER_ADMIN");
            MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(SystemMonitorController.class)).build();
            mvc.perform(get("/api/admin/system/monitor"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.refreshIntervalSeconds").value(15))
                    .andExpect(jsonPath("$.data.components[0].status").value("DOWN"))
                    .andExpect(jsonPath("$.data.components[0].metrics[0].value").isEmpty());
            verify(service).snapshot();
        }
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        SystemMonitorService service = mock(SystemMonitorService.class);
        context.register(SecurityConfiguration.class);
        context.registerBean(SystemMonitorService.class, () -> service);
        context.registerBean(SystemMonitorController.class, () -> new SystemMonitorController(service));
        context.refresh();
        return context;
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "synthetic-test-user", "unused", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityConfiguration {}
}
