package com.hzmwmt.common.handler;

import com.hzmwmt.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void preservesResponseStatusForHostAuthenticationFailures() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geo-agent-api/geo/tasks");

        ResponseEntity<Result<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "GEO administrator access is required."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("403");
    }
}
