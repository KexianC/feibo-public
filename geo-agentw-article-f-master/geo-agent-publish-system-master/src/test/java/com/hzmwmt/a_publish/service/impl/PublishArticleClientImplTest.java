package com.hzmwmt.a_publish.service.impl;

import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishArticleClientImplTest {

    private final PublishArticleClientImpl client = new PublishArticleClientImpl();

    @AfterEach
    void clearContext() {
        LoginContextHolder.clear();
    }

    @Test
    void capturesExactUserIdBeforeReactiveSubscription() {
        LoginContextHolder.setUser(1976200871068037122L, "13900000001", "Preview Admin", "ADMIN", true);

        Consumer<HttpHeaders> capturedHeaders = client.capturedUserHeaders();
        LoginContextHolder.clear();

        HttpHeaders headers = new HttpHeaders();
        capturedHeaders.accept(headers);
        assertEquals("1976200871068037122", headers.getFirst("userid"));
    }

    @Test
    void capturesEachUsersIdentityIndependently() {
        LoginContextHolder.setUser(42L, "user-a", "User A", "ADMIN", true);
        Consumer<HttpHeaders> userAHeaders = client.capturedUserHeaders();

        LoginContextHolder.setUser(84L, "user-b", "User B", "ADMIN", true);
        Consumer<HttpHeaders> userBHeaders = client.capturedUserHeaders();

        HttpHeaders first = new HttpHeaders();
        HttpHeaders second = new HttpHeaders();
        userAHeaders.accept(first);
        userBHeaders.accept(second);

        assertEquals("42", first.getFirst("userid"));
        assertEquals("84", second.getFirst("userid"));
    }

    @Test
    void rejectsMissingUserContextWithoutAdminFallback() {
        AppException exception = assertThrows(AppException.class, client::capturedUserHeaders);

        assertEquals("UNAUTHORIZED", exception.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
