package com.hzmwmt.infrastructure.gateway.netty;

import com.hzmwmt.infrastructure.properties.WebSocketServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTicketServiceTest {
    private RedissonClient redissonClient;
    private RMapCache<String, Long> tickets;
    private WebSocketTicketService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        tickets = mock(RMapCache.class);
        when(redissonClient.<String, Long>getMapCache(anyString())).thenReturn(tickets);
        service = new WebSocketTicketService(redissonClient, new WebSocketServerProperties());
    }

    @Test
    void issuedTicketIsOpaqueShortLivedAndBoundToUser() {
        when(tickets.putIfAbsent(
                anyString(),
                eq(42L),
                anyLong(),
                eq(TimeUnit.MILLISECONDS))).thenReturn(null);

        WebSocketTicketService.IssuedTicket issued = service.issue(42L);

        assertTrue(issued.value().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(30, issued.expiresInSeconds());
        verify(tickets).putIfAbsent(
                eq(issued.value()),
                eq(42L),
                eq(30_000L),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void ticketCanOnlyBeConsumedOnce() {
        String ticket = "A".repeat(43);
        when(tickets.remove(ticket)).thenReturn(42L, null);

        assertEquals(42L, service.consume(ticket));
        assertNull(service.consume(ticket));
    }

    @Test
    void malformedTicketIsRejectedBeforeRedisLookup() {
        assertNull(service.consume("../../not-a-ticket"));
        verify(redissonClient, never()).getMapCache(anyString());
    }
}
