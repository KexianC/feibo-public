package com.hzmwmt.integration.geo;

import com.hzmwmt.common.context.LoginContextHolder;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoHostAuthenticationInterceptorTest {

    private final GeoIntegrationProperties properties = new GeoIntegrationProperties();
    private final GeoHostAuthenticationInterceptor interceptor =
            new GeoHostAuthenticationInterceptor(properties);

    @AfterEach
    void clearContexts() {
        LoginContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingAuthentication() {
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(null, null, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void rejectsAuthenticatedNonAdmin() {
        authenticate("ROLE_USER");
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(null, null, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void rejectsOrdinaryAdministrator() {
        authenticate("ROLE_ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(null, null, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void rejectsMissingOrNonPositiveUserIdWithoutLeavingIdentity() {
        for (Long userId : new Long[]{null, 0L, -1L}) {
            authenticate("ROLE_SUPER_ADMIN", userId);
            LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> interceptor.preHandle(new MockHttpServletRequest(), null, null)
            );

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            assertNull(LoginContextHolder.currentUser());
        }
    }

    @Test
    void bridgesSuperAdminIdentityAndClearsItAfterRequest() {
        authenticate("ROLE_SUPER_ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(interceptor.preHandle(request, null, null));
        LoginContextHolder.LoginUser currentUser = LoginContextHolder.currentUser();
        assertEquals(42L, currentUser.userId());
        assertEquals("13800000000", currentUser.username());
        assertEquals("飞驳管理员", currentUser.displayName());
        assertEquals("ADMIN", currentUser.role());
        assertTrue(currentUser.loggedIn());

        interceptor.afterCompletion(request, null, null, null);
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void clearsIdentityWhenServletSwitchesToAsyncHandling() {
        authenticate("ROLE_SUPER_ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertTrue(interceptor.preHandle(request, null, null));

        interceptor.afterConcurrentHandlingStarted(request, null, null);

        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void restoresSnapshottedIdentityOnAsyncRedispatchWithoutSecurityContext() {
        authenticate("ROLE_SUPER_ADMIN");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertTrue(interceptor.preHandle(request, null, null));

        interceptor.afterConcurrentHandlingStarted(request, null, null);
        SecurityContextHolder.clearContext();
        request.setDispatcherType(DispatcherType.ASYNC);

        assertTrue(interceptor.preHandle(request, null, null));
        LoginContextHolder.LoginUser currentUser = LoginContextHolder.currentUser();
        assertEquals(42L, currentUser.userId());
        assertEquals("13800000000", currentUser.username());
        assertEquals("飞驳管理员", currentUser.displayName());
        assertEquals("ADMIN", currentUser.role());

        interceptor.afterCompletion(request, null, null, null);
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void rejectsAsyncRedispatchWithoutSnapshotAndClearsStaleIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interceptor.preHandle(request, null, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertNull(LoginContextHolder.currentUser());
    }

    private void authenticate(String authority) {
        authenticate(authority, 42L);
    }

    private void authenticate(String authority, Long userId) {
        HostPrincipal principal = new HostPrincipal(userId, "13800000000", "飞驳管理员");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    static final class HostPrincipal {
        private final Long userId;
        private final String phoneNumber;
        private final String nickName;

        HostPrincipal(Long userId, String phoneNumber, String nickName) {
            this.userId = userId;
            this.phoneNumber = phoneNumber;
            this.nickName = nickName;
        }

        public Long getUserId() {
            return userId;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public String getNickName() {
            return nickName;
        }
    }
}
