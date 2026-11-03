package com.hzmwmt.security;

import com.hzmwmt.domain.mapper.role.AdminPermissionMapper;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.mapper.role.AdminRolePermissionMapMapper;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.domain.service.role.impl.AdminPermissionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityRbacMethodSecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryAdminCannotManageRolePermissionsEvenWithIdentityCapability() {
        try (AnnotationConfigApplicationContext context = securedContext()) {
            authenticate("ROLE_ADMIN", "identity.manage");

            AdminPermissionService service = context.getBean(AdminPermissionService.class);
            assertThatThrownBy(service::listPermissions)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void superAdminRoleFallbackCanReadCatalog() {
        try (AnnotationConfigApplicationContext context = securedContext()) {
            authenticate("ROLE_SUPER_ADMIN");

            AdminPermissionService service = context.getBean(AdminPermissionService.class);
            assertThat(service.listPermissions()).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext securedContext() {
        AdminPermissionMapper permissionMapper = mock(AdminPermissionMapper.class);
        AdminRolePermissionMapMapper rolePermissionMapper = mock(AdminRolePermissionMapMapper.class);
        AdminRoleMapper roleMapper = mock(AdminRoleMapper.class);
        when(permissionMapper.selectAllOrdered()).thenReturn(List.of());

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfiguration.class);
        context.registerBean(AdminPermissionMapper.class, () -> permissionMapper);
        context.registerBean(AdminRolePermissionMapMapper.class, () -> rolePermissionMapper);
        context.registerBean(AdminRoleMapper.class, () -> roleMapper);
        context.registerBean(AdminPermissionServiceImpl.class, () ->
                new AdminPermissionServiceImpl(permissionMapper, rolePermissionMapper, roleMapper));
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
