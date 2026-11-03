package com.hzmwmt.security;

import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.model.entity.RoleEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterRoleHierarchyTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminInheritsAdminAuthority() throws Exception {
        authenticateWithRoles("SUPER_ADMIN");

        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), currentAuthorities());
    }

    @Test
    void ordinaryAdminDoesNotInheritSuperAdminAuthority() throws Exception {
        authenticateWithRoles("ADMIN");

        assertEquals(Set.of("ROLE_ADMIN"), currentAuthorities());
    }

    @Test
    void existingDualRoleMappingRemainsCompatible() throws Exception {
        authenticateWithRoles("ADMIN", "SUPER_ADMIN");

        Set<String> authorities = currentAuthorities();
        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), authorities);
        assertTrue(authorities.contains("ROLE_ADMIN"));
    }

    @Test
    void databaseCapabilitiesAreAddedToEveryAuthenticatedRequest() throws Exception {
        authenticateWithPermissions(List.of("admin.console", "user.read"), "ADMIN");

        assertEquals(
                Set.of("ROLE_ADMIN", "admin.console", "user.read"),
                currentAuthorities());
    }

    @Test
    void onlyRotatingPostUsesRefreshTokenTypeMatcher() {
        IAuthService authService = mock(IAuthService.class);
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authService,
                new RoleHierarchyAuthoritiesMapper(hierarchy),
                emptyPermissionService());

        assertEquals(false, filter.isRefreshTokenRequest(
                new MockHttpServletRequest("GET", "/api/v1/auth/refresh")));
        assertTrue(filter.isRefreshTokenRequest(
                new MockHttpServletRequest("POST", "/api/v1/auth/refresh/rotate")));
        assertEquals(false, filter.isRefreshTokenRequest(
                new MockHttpServletRequest("POST", "/api/v1/auth/refresh")));
        assertEquals(false, filter.isRefreshTokenRequest(
                new MockHttpServletRequest("GET", "/api/v1/auth/refresh/rotate")));
        assertEquals(false, filter.isRefreshTokenRequest(
                new MockHttpServletRequest("GET", "/api/v1/profile/auth/refresh-history")));
    }

    @Test
    void refreshStateStoreOutageIsRetryableAndDoesNotMasqueradeAsExpiredSession() throws Exception {
        IAuthService authService = mock(IAuthService.class);
        when(authService.validateToken(any())).thenThrow(new IllegalStateException("redis unavailable"));
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authService,
                new RoleHierarchyAuthoritiesMapper(hierarchy),
                emptyPermissionService());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh/rotate");
        request.addHeader("Authorization", "Bearer refresh-token");
        request.addHeader("client-fingerprint", "test-device");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains(ResponseCode.SERVICE_ERROR.getCode()));
    }

    @Test
    void unavailablePermissionStoreFailsClosedAsRetryable503() throws Exception {
        IAuthService authService = mock(IAuthService.class);
        AuthUserAggregate aggregate = AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder().userId(1001L).build())
                .roleEntityList(List.of(RoleEntity.builder().name("ADMIN").build()))
                .build();
        when(authService.validateToken(any())).thenReturn(aggregate);
        AdminPermissionService permissionService = mock(AdminPermissionService.class);
        when(permissionService.getPermissionCodesByUserId(1001L))
                .thenThrow(new AuthorizationDataUnavailableException(
                        new IllegalStateException("database unavailable")));
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authService,
                new RoleHierarchyAuthoritiesMapper(hierarchy),
                permissionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/test");
        request.addHeader("Authorization", "Bearer access-token");
        request.addHeader("client-fingerprint", "test-device");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains(ResponseCode.SERVICE_ERROR.getCode()));
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void superAdminAccessWithoutMfaAssuranceAuthenticates() throws Exception {
        MockHttpServletResponse response = authenticateRequest(
                false, "GET", "/api/admin/test", List.of(), "SUPER_ADMIN");

        assertEquals(200, response.getStatus());
        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), currentAuthorities());
    }

    @Test
    void legacySuperAdminRoleAliasWithoutMfaAssuranceAuthenticates() throws Exception {
        MockHttpServletResponse response = authenticateRequest(
                false, "GET", "/api/admin/test", List.of(), "SUPERADMIN");

        assertEquals(200, response.getStatus());
        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), currentAuthorities());
    }

    @Test
    void superAdminRefreshWithoutMfaAssuranceAuthenticates() throws Exception {
        MockHttpServletResponse response = authenticateRequest(
                false, "POST", "/api/v1/auth/refresh/rotate", List.of(), "SUPER_ADMIN");

        assertEquals(200, response.getStatus());
        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), currentAuthorities());
    }

    @Test
    void legacyMfaAssuredSuperAdminRefreshRemainsCompatible() throws Exception {
        MockHttpServletResponse response = authenticateRequest(
                true, "POST", "/api/v1/auth/refresh/rotate", List.of(), "SUPER_ADMIN");

        assertEquals(200, response.getStatus());
        assertEquals(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN"), currentAuthorities());
    }

    private void authenticateWithRoles(String... roles) throws Exception {
        authenticateWithPermissions(List.of(), roles);
    }

    private void authenticateWithPermissions(List<String> permissions, String... roles) throws Exception {
        boolean mfaVerified = List.of(roles).stream()
                .anyMatch("SUPER_ADMIN"::equals);
        authenticateRequest(mfaVerified, "GET", "/api/admin/test", permissions, roles);
    }

    private MockHttpServletResponse authenticateRequest(
            boolean mfaVerified,
            String method,
            String path,
            List<String> permissions,
            String... roles) throws Exception {
        IAuthService authService = mock(IAuthService.class);
        AuthUserAggregate aggregate = AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder().userId(1001L).build())
                .roleEntityList(List.of(roles).stream()
                        .map(role -> RoleEntity.builder().name(role).build())
                        .toList())
                .build();
        when(authService.validateToken(any())).thenAnswer(invocation -> {
            JwtRuleCommandEntity command = invocation.getArgument(0);
            command.setMfaVerified(mfaVerified);
            return aggregate;
        });

        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy(
                "ROLE_SUPER_ADMIN > ROLE_ADMIN");
        AdminPermissionService permissionService = mock(AdminPermissionService.class);
        when(permissionService.getPermissionCodesByUserId(1001L)).thenReturn(permissions);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                authService,
                new RoleHierarchyAuthoritiesMapper(hierarchy),
                permissionService);

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("client-fingerprint", "test-device");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private Set<String> currentAuthorities() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }

    private AdminPermissionService emptyPermissionService() {
        AdminPermissionService permissionService = mock(AdminPermissionService.class);
        when(permissionService.getPermissionCodesByUserId(any())).thenReturn(List.of());
        return permissionService;
    }
}
