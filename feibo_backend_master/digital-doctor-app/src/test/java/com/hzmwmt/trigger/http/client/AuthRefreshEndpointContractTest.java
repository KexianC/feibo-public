package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.IAuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRefreshEndpointContractTest {

    @Test
    void legacyRefreshContractIsRemovedAndPostRotationRemains() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> IAuthApi.class.getMethod("refreshToken"));
        assertThrows(NoSuchMethodException.class, () -> AuthController.class.getMethod("refreshToken"));

        Method rotating = AuthController.class.getMethod("rotateRefreshToken");
        PostMapping rotatingMapping = rotating.getAnnotation(PostMapping.class);
        assertArrayEquals(new String[]{"refresh/rotate"}, rotatingMapping.value());
    }
}
