package com.hzmwmt.security;

import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.controller.user.UserSignController;
import com.hzmwmt.domain.service.user.UserSignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSignControllerOwnershipTest {

    private UserSignService signService;
    private UserSignController controller;

    @BeforeEach
    void setUp() {
        signService = mock(UserSignService.class);
        IAuthService authService = mock(IAuthService.class);
        when(authService.queryValidatedUser())
                .thenReturn(AuthUserEntity.builder().userId(200L).build());

        controller = new UserSignController();
        ReflectionTestUtils.setField(controller, "userSignService", signService);
        ReflectionTestUtils.setField(controller, "authService", authService);
    }

    @Test
    void signAndCheckAlwaysUseAuthenticatedUserId() {
        when(signService.checkSign(200L)).thenReturn(true);

        controller.sign();
        controller.checkSign();

        verify(signService).sign(200L);
        verify(signService).checkSign(200L);
    }

    @Test
    void stateChangingSignEndpointIsPostAndAcceptsNoClientUserId() throws Exception {
        var method = UserSignController.class.getMethod("sign");

        assertThat(method.getParameterCount()).isZero();
        assertThat(method.getAnnotation(PostMapping.class)).isNotNull();
    }
}
