package com.hzmwmt.videogeneration;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoGenerationSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean VideoGenerationService service() { return mock(VideoGenerationService.class); }
        @Bean VideoGenerationAdminController controller(VideoGenerationService service) { return new VideoGenerationAdminController(service); }
    }
    @Test void ordinaryAdminCannotReadOrMutateVideoSettings() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var controller = context.getBean(VideoGenerationAdminController.class);
            assertThatThrownBy(controller::providers).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.save("seedance", new VideoGenerationService.Settings("test", "model", true, "secret", 1))).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(VideoGenerationService.class));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void superAdminCanReadConfiguration() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
            context.getBean(VideoGenerationAdminController.class).providers();
            verify(context.getBean(VideoGenerationService.class)).providers(true);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
