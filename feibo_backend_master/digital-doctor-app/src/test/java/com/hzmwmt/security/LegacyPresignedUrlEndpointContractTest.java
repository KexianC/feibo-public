package com.hzmwmt.security;

import com.hzmwmt.api.controller.FileController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPresignedUrlEndpointContractTest {

    @Test
    void arbitraryUrlPresigningControllerIsNotRegisteredOrMapped() throws Exception {
        assertThat(FileController.class.getAnnotation(RestController.class)).isNull();
        assertThat(FileController.class
                .getMethod("getPresignedUrl", String.class, int.class)
                .getAnnotation(GetMapping.class)).isNull();
    }
}
