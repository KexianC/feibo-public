package com.hzmwmt.security;

import com.hzmwmt.domain.controller.dashboard.DashboardController;
import com.hzmwmt.domain.mapper.dashboard.DashboardStatisticsMapper;
import com.hzmwmt.domain.controller.order.AdminOrderController;
import com.hzmwmt.domain.controller.user.AdminConversationController;
import com.hzmwmt.domain.service.dashboard.IDashboardService;
import com.hzmwmt.domain.service.order.AdminOrderService;
import com.hzmwmt.domain.service.user.AdminConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCapabilityMethodSecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consoleCapabilityAllowsNormalReadAndMissingCapabilityIsDenied() {
        try (AnnotationConfigApplicationContext context = securedContext()) {
            IDashboardService service = context.getBean(IDashboardService.class);
            when(service.getUserOverview()).thenReturn(Map.of("total", 1));
            DashboardController controller = context.getBean(DashboardController.class);

            authenticate("ROLE_ADMIN");
            assertThatThrownBy(controller::getUserOverview)
                    .isInstanceOf(AccessDeniedException.class);

            authenticate("ROLE_ADMIN", "admin.console");
            assertThat(controller.getUserOverview()).containsEntry("total", 1);
        }
    }

    @Test
    void userReadCapabilityCannotCrossMutationHardGate() {
        try (AnnotationConfigApplicationContext context = securedContext()) {
            AdminConversationService service = context.getBean(AdminConversationService.class);
            when(service.deleteConversation("conversation-1")).thenReturn(true);
            AdminConversationController controller = context.getBean(AdminConversationController.class);

            authenticate("ROLE_ADMIN", "user.read");
            assertThatThrownBy(() -> controller.deleteConversation("conversation-1"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(service, never()).deleteConversation("conversation-1");

            authenticate("ROLE_SUPER_ADMIN");
            assertThat(controller.deleteConversation("conversation-1").getData()).isTrue();
        }
    }

    @Test
    void financeCapabilityAllowsReadButCannotCrossMutationHardGate() {
        try (AnnotationConfigApplicationContext context = securedContext()) {
            AdminOrderService service = context.getBean(AdminOrderService.class);
            when(service.getExpiredOrders()).thenReturn(List.of());
            when(service.updateOrderStatus(88L, 3)).thenReturn(true);
            AdminOrderController controller = context.getBean(AdminOrderController.class);

            authenticate("ROLE_ADMIN", "finance.manage");
            assertThat(controller.getExpiredOrders().getData()).isEmpty();
            assertThatThrownBy(() -> controller.updateStatus(88L, 3))
                    .isInstanceOf(AccessDeniedException.class);
            verify(service, never()).updateOrderStatus(88L, 3);

            authenticate("ROLE_SUPER_ADMIN");
            assertThat(controller.updateStatus(88L, 3).getData()).isTrue();
        }
    }

    private AnnotationConfigApplicationContext securedContext() {
        IDashboardService dashboardService = mock(IDashboardService.class);
        AdminConversationService conversationService = mock(AdminConversationService.class);
        AdminOrderService orderService = mock(AdminOrderService.class);

        DashboardController dashboardController = new DashboardController();
        ReflectionTestUtils.setField(dashboardController, "dashboardService", dashboardService);
        AdminConversationController conversationController = new AdminConversationController();
        ReflectionTestUtils.setField(
                conversationController,
                "adminConversationService",
                conversationService);
        AdminOrderController orderController = new AdminOrderController();
        ReflectionTestUtils.setField(orderController, "adminOrderService", orderService);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfiguration.class);
        context.registerBean(IDashboardService.class, () -> dashboardService);
        context.registerBean(DashboardStatisticsMapper.class, () -> mock(DashboardStatisticsMapper.class));
        context.registerBean(AdminConversationService.class, () -> conversationService);
        context.registerBean(AdminOrderService.class, () -> orderService);
        context.registerBean(DashboardController.class, () -> dashboardController);
        context.registerBean(AdminConversationController.class, () -> conversationController);
        context.registerBean(AdminOrderController.class, () -> orderController);
        context.refresh();
        return context;
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester",
                        "n/a",
                        List.of(authorities).stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
