package com.hzmwmt.security;

import com.hzmwmt.domain.controller.function.AdminDigitalMusicController;
import com.hzmwmt.domain.controller.function.AdminMusicController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminMusicEndpointContractTest {

    @Test
    void legacyRootMusicControllerIsNotRegistered() {
        assertNull(AdminMusicController.class.getAnnotation(RestController.class));
    }

    @Test
    void supportedMusicControllerStaysUnderAdminPath() {
        RestController restController = AdminDigitalMusicController.class.getAnnotation(RestController.class);
        RequestMapping requestMapping = AdminDigitalMusicController.class.getAnnotation(RequestMapping.class);
        PreAuthorize preAuthorize = AdminDigitalMusicController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(restController);
        assertNotNull(requestMapping);
        assertArrayEquals(new String[]{"/api/admin/function/music"}, requestMapping.value());
        assertNotNull(preAuthorize);
        assertEquals("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')", preAuthorize.value());
    }
}
