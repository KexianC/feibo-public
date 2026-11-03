package com.hzmwmt.infrastructure.gateway.netty;

import com.hzmwmt.infrastructure.properties.WebSocketServerProperties;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class WebSocketTicketService {
    public static final String SUBPROTOCOL = "feibo.ws.v1";
    public static final String TICKET_PROTOCOL_PREFIX = "ticket.";

    private static final String TICKET_MAP_KEY = "feibo:websocket:tickets";
    private static final Pattern VALID_TICKET = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final RedissonClient redissonClient;
    private final WebSocketServerProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebSocketTicketService(
            RedissonClient redissonClient,
            WebSocketServerProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    public IssuedTicket issue(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        Duration ttl = validatedTicketTtl();
        RMapCache<String, Long> tickets = redissonClient.getMapCache(TICKET_MAP_KEY);
        for (int attempt = 0; attempt < 3; attempt++) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            Long existing = tickets.putIfAbsent(
                    ticket,
                    userId,
                    ttl.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (existing == null) {
                return new IssuedTicket(ticket, Math.max(1, ttl.toSeconds()));
            }
        }
        throw new IllegalStateException("Unable to issue a unique WebSocket ticket");
    }

    /**
     * Atomically removes and returns the ticket owner. A ticket can therefore
     * authenticate exactly one handshake across all application instances.
     */
    public Long consume(String ticket) {
        if (ticket == null || !VALID_TICKET.matcher(ticket).matches()) {
            return null;
        }
        return redissonClient.<String, Long>getMapCache(TICKET_MAP_KEY).remove(ticket);
    }

    private Duration validatedTicketTtl() {
        Duration ttl = properties.getTicketTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException("WebSocket ticket TTL must be between 1 ms and 2 minutes");
        }
        return ttl;
    }

    public record IssuedTicket(String value, long expiresInSeconds) {
    }
}
