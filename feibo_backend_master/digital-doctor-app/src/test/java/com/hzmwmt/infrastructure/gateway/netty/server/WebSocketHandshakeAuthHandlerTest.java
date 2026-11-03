package com.hzmwmt.infrastructure.gateway.netty.server;

import com.hzmwmt.infrastructure.gateway.netty.WebSocketTicketService;
import com.hzmwmt.infrastructure.properties.WebSocketServerProperties;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketHandshakeAuthHandlerTest {
    private static final String ORIGIN = "https://dd.hzmwmt.com";
    private static final String TICKET = "A".repeat(43);

    private WebSocketTicketService ticketService;
    private WebSocketServerProperties properties;

    @BeforeEach
    void setUp() {
        ticketService = mock(WebSocketTicketService.class);
        properties = new WebSocketServerProperties();
        properties.setAllowedOrigins(List.of(ORIGIN));
    }

    @Test
    void validTicketBindsAuthenticatedUserAndForwardsHandshake() {
        when(ticketService.consume(TICKET)).thenReturn(42L);
        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(ticketService, properties));

        assertTrue(channel.writeInbound(handshake(ORIGIN, TICKET)));
        FullHttpRequest forwarded = channel.readInbound();
        assertNotNull(forwarded);
        assertEquals(42L, channel.attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).get());
        forwarded.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void missingTicketFailsClosed() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(ticketService, properties));
        FullHttpRequest request = handshake(ORIGIN, null);

        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        response.release();
        verify(ticketService, never()).consume(TICKET);
        channel.finishAndReleaseAll();
    }

    @Test
    void untrustedOriginIsRejectedBeforeTicketConsumption() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(ticketService, properties));

        channel.writeInbound(handshake("https://evil.example", TICKET));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.FORBIDDEN, response.status());
        response.release();
        verify(ticketService, never()).consume(TICKET);
        channel.finishAndReleaseAll();
    }

    @Test
    void replayedTicketIsRejected() {
        when(ticketService.consume(TICKET)).thenReturn(null);
        EmbeddedChannel channel = new EmbeddedChannel(
                new WebSocketHandshakeAuthHandler(ticketService, properties));

        channel.writeInbound(handshake(ORIGIN, TICKET));

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        response.release();
        verify(ticketService).consume(TICKET);
        channel.finishAndReleaseAll();
    }

    private FullHttpRequest handshake(String origin, String ticket) {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/ws",
                Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.ORIGIN, origin);
        String protocols = WebSocketTicketService.SUBPROTOCOL;
        if (ticket != null) {
            protocols += ", " + WebSocketTicketService.TICKET_PROTOCOL_PREFIX + ticket;
        }
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, protocols);
        return request;
    }
}
