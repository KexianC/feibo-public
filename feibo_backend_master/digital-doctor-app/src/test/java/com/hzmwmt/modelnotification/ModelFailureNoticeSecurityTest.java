package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelFailureNoticeSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ModelFailureNoticeStore store() { return mock(ModelFailureNoticeStore.class); }
        @Bean IAuthService auth() { return mock(IAuthService.class); }
        @Bean ModelFailureNoticeService service(ModelFailureNoticeStore store, IAuthService auth) {
            return new ModelFailureNoticeService(store, auth);
        }
        @Bean ModelFailureNoticeController controller(ModelFailureNoticeService service) {
            return new ModelFailureNoticeController(service);
        }
    }

    @AfterEach void clearAuth() { SecurityContextHolder.clearContext(); }

    @Test void usersAndOrdinaryAdministratorsCannotReadOrMarkAnyNotification() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(ModelFailureNoticeController.class);
            var service = context.getBean(ModelFailureNoticeService.class);
            for (String role : List.of("ROLE_USER", "ROLE_ADMIN")) {
                authenticate(role);
                assertThatThrownBy(() -> controller.list(1, 20)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(controller::unreadCount).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controller.markRead("1")).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(controller::markAllRead).isInstanceOf(AccessDeniedException.class);
                // The real service has a separate permission boundary, too.
                assertThatThrownBy(() -> service.list(1, 20)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(service::unreadCount).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> service.markRead("1")).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(service::markAllRead).isInstanceOf(AccessDeniedException.class);
            }
            verifyNoInteractions(context.getBean(ModelFailureNoticeStore.class), context.getBean(IAuthService.class));
        }
    }

    @Test void unauthenticatedCallsCannotReadNotifications() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            assertThatThrownBy(context.getBean(ModelFailureNoticeController.class)::unreadCount)
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            verifyNoInteractions(context.getBean(ModelFailureNoticeStore.class), context.getBean(IAuthService.class));
        }
    }

    @Test void superAdministratorAccessUsesOnlyTheirAuthenticatedUserId() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ROLE_SUPER_ADMIN");
            var auth = context.getBean(IAuthService.class);
            var store = context.getBean(ModelFailureNoticeStore.class);
            when(auth.queryValidatedUser()).thenReturn(AuthUserEntity.builder().userId(9007199254740993L).build());
            when(store.exists(42L)).thenReturn(true);
            var controller = context.getBean(ModelFailureNoticeController.class);
            assertThat(controller.list(1, 20).getCode()).isEqualTo(200);
            controller.unreadCount();
            controller.markRead("42");
            controller.markAllRead();
            verify(store).list(9007199254740993L, 20, 0);
            verify(store).markRead(9007199254740993L, 42L);
            verify(store).markAllRead(9007199254740993L);
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority(role))));
    }
}
