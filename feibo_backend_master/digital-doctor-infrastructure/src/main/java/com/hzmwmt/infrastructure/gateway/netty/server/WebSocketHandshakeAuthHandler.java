package com.hzmwmt.infrastructure.gateway.netty.server;

import com.hzmwmt.infrastructure.gateway.netty.WebSocketTicketService;
import com.hzmwmt.infrastructure.properties.WebSocketServerProperties;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;

import java.util.Arrays;

final class WebSocketHandshakeAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    static final AttributeKey<Long> AUTHENTICATED_USER_ID =
            AttributeKey.valueOf("feibo.authenticatedUserId");

    private final WebSocketTicketService ticketService;
    private final WebSocketServerProperties properties;

    WebSocketHandshakeAuthHandler(
            WebSocketTicketService ticketService,
            WebSocketServerProperties properties) {
        this.ticketService = ticketService;
        this.properties = properties;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()
                || !"/ws".equals(new QueryStringDecoder(request.uri()).path())) {
            reject(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin == null || properties.getAllowedOrigins() == null
                || properties.getAllowedOrigins().stream().noneMatch(origin::equalsIgnoreCase)) {
            reject(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        String protocols = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
        if (protocols == null) {
            reject(ctx, HttpResponseStatus.UNAUTHORIZED);
            return;
        }
        String[] requestedProtocols = Arrays.stream(protocols.split(","))
                .map(String::trim)
                .toArray(String[]::new);
        boolean supportedProtocol = Arrays.asList(requestedProtocols)
                .contains(WebSocketTicketService.SUBPROTOCOL);
        String ticket = Arrays.stream(requestedProtocols)
                .filter(value -> value.startsWith(WebSocketTicketService.TICKET_PROTOCOL_PREFIX))
                .map(value -> value.substring(WebSocketTicketService.TICKET_PROTOCOL_PREFIX.length()))
                .findFirst()
                .orElse(null);
        if (!supportedProtocol || ticket == null) {
            reject(ctx, HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        Long userId = ticketService.consume(ticket);
        if (userId == null || userId <= 0) {
            reject(ctx, HttpResponseStatus.UNAUTHORIZED);
            return;
        }
        ctx.channel().attr(AUTHENTICATED_USER_ID).set(userId);
        ctx.fireChannelRead(request.retain());
    }

    private void reject(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.EMPTY_BUFFER);
        HttpUtil.setContentLength(response, 0);
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
