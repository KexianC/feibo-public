package com.hzmwmt.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpringSecurityCorsConfigurationTest {

    private static final String WORKBENCH_ORIGIN = "https://dd.hzmwmt.com";
    private static final String MANAGEMENT_ORIGIN = "https://manage.hzmwmt.com";

    @Test
    void legitimatePreflightReceivesOnlyTheExactConfiguredOrigin() throws Exception {
        MockMvc mockMvc = mockMvc(corsSource(WORKBENCH_ORIGIN, MANAGEMENT_ORIGIN));

        mockMvc.perform(options("/probe")
                        .header(HttpHeaders.ORIGIN, MANAGEMENT_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization, content-type, client-fingerprint, idempotency-token, last-event-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, MANAGEMENT_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.VARY,
                        org.hamcrest.Matchers.containsString(HttpHeaders.ORIGIN)));
    }

    @Test
    void attackerOriginGetsNeitherPreflightApprovalNorAcaoHeader() throws Exception {
        MockMvc mockMvc = mockMvc(corsSource(WORKBENCH_ORIGIN, MANAGEMENT_ORIGIN));
        String attackerOrigin = "https://dd.hzmwmt.com.attacker.example";

        mockMvc.perform(options("/probe")
                        .header(HttpHeaders.ORIGIN, attackerOrigin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mockMvc.perform(get("/probe").header(HttpHeaders.ORIGIN, attackerOrigin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowedActualRequestRetainsCredentialedFrontendCompatibility() throws Exception {
        MockMvc mockMvc = mockMvc(corsSource(WORKBENCH_ORIGIN));

        mockMvc.perform(get("/probe").header(HttpHeaders.ORIGIN, WORKBENCH_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WORKBENCH_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsConfigurationContainsNoWildcardOrOriginPattern() {
        CorsConfigurationSource source = corsSource(WORKBENCH_ORIGIN);
        CorsConfiguration configuration = source.getCorsConfiguration(requestFor("/probe"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(WORKBENCH_ORIGIN);
        assertThat(configuration.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(configuration.getAllowedHeaders())
                .containsExactly(
                        "Accept",
                        "Authorization",
                        "Content-Type",
                        "Client-Fingerprint",
                        "Idempotency-Token",
                        "Last-Event-ID")
                .doesNotContain("*");
    }

    @Test
    void wildcardAndNonOriginConfigurationFailClosed() {
        HttpCorsProperties wildcard = properties("https://*.hzmwmt.com");
        HttpCorsProperties path = properties("https://dd.hzmwmt.com/application");
        HttpCorsProperties empty = properties(" ");

        assertThatThrownBy(wildcard::validatedAllowedOrigins)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact HTTP(S) origins");
        assertThatThrownBy(path::validatedAllowedOrigins)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact HTTP(S) origins");
        assertThatThrownBy(empty::validatedAllowedOrigins)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one exact origin");
    }

    private static CorsConfigurationSource corsSource(String... origins) {
        return new SpringSecurityConfig().corsConfigurationSource(properties(origins));
    }

    private static HttpCorsProperties properties(String... origins) {
        HttpCorsProperties properties = new HttpCorsProperties();
        properties.setAllowedOrigins(List.of(origins));
        properties.setAllowCredentials(true);
        return properties;
    }

    private static MockMvc mockMvc(CorsConfigurationSource source) {
        return MockMvcBuilders.standaloneSetup(new CorsProbeController())
                .addFilters(new CorsFilter(source))
                .build();
    }

    private static HttpServletRequest requestFor(String path) {
        return new org.springframework.mock.web.MockHttpServletRequest("GET", path);
    }

    @RestController
    @RequestMapping("/probe")
    private static class CorsProbeController {
        @GetMapping
        ResponseEntity<Void> get() {
            return ResponseEntity.ok().build();
        }

        @PostMapping
        ResponseEntity<Void> post() {
            return ResponseEntity.ok().build();
        }
    }
}
