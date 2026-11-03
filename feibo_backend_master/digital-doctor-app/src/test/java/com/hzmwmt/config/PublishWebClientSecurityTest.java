package com.hzmwmt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishWebClientSecurityTest {

    @Test
    void publishClientFailsClosedWhenInternalTokenIsMissingOrWeak() {
        WebClientConfig config = new WebClientConfig();
        WebClientConfigProperties properties = new WebClientConfigProperties();

        assertThrows(IllegalStateException.class,
                () -> config.videoWebClient(properties, ""));
        assertThrows(IllegalStateException.class,
                () -> config.videoWebClient(properties, "short-token"));
    }
}
