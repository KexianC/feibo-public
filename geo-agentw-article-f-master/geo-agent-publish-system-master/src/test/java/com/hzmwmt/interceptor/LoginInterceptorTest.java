package com.hzmwmt.interceptor;

import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.LoginConstants;
import com.hzmwmt.service.LoginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginInterceptorTest {

    private LoginInterceptor interceptor;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        LoginContextHolder.clear();
        loginService = mock(LoginService.class);
        AppSecurityProperties securityProperties = new AppSecurityProperties();
        securityProperties.setCsrfHeaderName("X-CSRF-Token");
        interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "loginService", loginService);
        ReflectionTestUtils.setField(interceptor, "securityProperties", securityProperties);
    }

    @AfterEach
    void clearContext() {
        LoginContextHolder.clear();
    }

    @Test
    void setsIdentityOnlyAfterSessionValidationSucceeds() {
        MockHttpServletRequest request = authenticatedRequest("GET", 42L);

        assertTrue(interceptor.preHandle(request, null, null));
        LoginContextHolder.LoginUser currentUser = LoginContextHolder.currentUser();
        assertEquals(42L, currentUser.userId());
        assertEquals("geo-user", currentUser.username());
        assertEquals("GEO 用户", currentUser.displayName());
        assertEquals("ADMIN", currentUser.role());

        interceptor.afterCompletion(request, null, null, null);
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void clearsIdentityWhenAuthenticationIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geo-agent-api/geo/tasks");
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        AppException exception = assertThrows(
                AppException.class,
                () -> interceptor.preHandle(request, null, null)
        );

        assertEquals("UNAUTHORIZED", exception.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void clearsIdentityWhenCsrfValidationFails() {
        MockHttpServletRequest request = authenticatedRequest("POST", 42L);
        request.getSession(false).setAttribute(LoginConstants.SESSION_CSRF_TOKEN, "expected-token");
        request.addHeader("X-CSRF-Token", "wrong-token");
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        AppException exception = assertThrows(
                AppException.class,
                () -> interceptor.preHandle(request, null, null)
        );

        assertEquals("CSRF_INVALID", exception.getCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertNull(LoginContextHolder.currentUser());
    }

    @Test
    void rejectsMissingOrNonPositiveSessionUserIdWithoutLeavingIdentity() {
        for (Long userId : new Long[]{null, 0L, -1L}) {
            MockHttpServletRequest request = authenticatedRequest("GET", userId);
            LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

            AppException exception = assertThrows(
                    AppException.class,
                    () -> interceptor.preHandle(request, null, null)
            );

            assertEquals("UNAUTHORIZED", exception.getCode());
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
            assertNull(LoginContextHolder.currentUser());
        }
    }

    @Test
    void optionsRequestCannotInheritIdentityFromReusedThread() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/geo-agent-api/geo/tasks");
        LoginContextHolder.setUser(99L, "stale", "stale", "ADMIN", true);

        assertTrue(interceptor.preHandle(request, null, null));

        assertNull(LoginContextHolder.currentUser());
    }

    private MockHttpServletRequest authenticatedRequest(String method, Long userId) {
        MockHttpSession session = new MockHttpSession();
        if (userId != null) {
            session.setAttribute(LoginConstants.SESSION_USER_ID, userId);
        }
        session.setAttribute(LoginConstants.SESSION_USERNAME, "geo-user");
        session.setAttribute(LoginConstants.SESSION_DISPLAY_NAME, "GEO 用户");
        session.setAttribute(LoginConstants.SESSION_ROLE, "ADMIN");
        when(loginService.isLoggedIn(session)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest(method, "/geo-agent-api/geo/tasks");
        request.setSession(session);
        return request;
    }
}
