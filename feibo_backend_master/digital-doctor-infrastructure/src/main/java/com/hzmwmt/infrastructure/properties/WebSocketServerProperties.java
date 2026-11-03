package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "websocket.server")
public class WebSocketServerProperties {
    private boolean enabled = true;
    private String bindAddress = "127.0.0.1";
    private int port = 8092;
    private List<String> allowedOrigins = List.of(
            "https://dd.hzmwmt.com",
            "https://www.hzmwmt.com",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4173",
            "http://127.0.0.1:4173");
    private Duration ticketTtl = Duration.ofSeconds(30);
    private Duration readerIdleTimeout = Duration.ofSeconds(90);
    private int maxFramePayloadLength = 1024 * 1024;
}
