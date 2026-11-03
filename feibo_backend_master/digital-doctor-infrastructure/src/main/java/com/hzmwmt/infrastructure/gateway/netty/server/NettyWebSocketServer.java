package com.hzmwmt.infrastructure.gateway.netty.server;

import com.hzmwmt.infrastructure.gateway.netty.WebSocketTicketService;
import com.hzmwmt.infrastructure.properties.WebSocketServerProperties;
import com.hzmwmt.types.enums.ResponseCode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NettyWebSocketServer {
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ApplicationContext applicationContext;
    private final WebSocketTicketService ticketService;
    private final WebSocketServerProperties properties;
    private volatile Channel serverChannel;

    public NettyWebSocketServer(
            ApplicationContext applicationContext,
            WebSocketTicketService ticketService,
            WebSocketServerProperties properties) {
        this.applicationContext = applicationContext;
        this.ticketService = ticketService;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.warn("Netty WebSocket server is disabled");
            return;
        }
        Thread serverThread = new Thread(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new HttpObjectAggregator(65536));
                                pipeline.addLast(new WebSocketHandshakeAuthHandler(ticketService, properties));
                                pipeline.addLast(new WebSocketServerProtocolHandler(
                                        "/ws",
                                        WebSocketTicketService.SUBPROTOCOL,
                                        true,
                                        properties.getMaxFramePayloadLength()));
                                pipeline.addLast(new IdleStateHandler(
                                        properties.getReaderIdleTimeout().toSeconds(),
                                        0,
                                        0,
                                        TimeUnit.SECONDS));
                                pipeline.addLast(new NettyServerHandler(applicationContext));
                            }
                        });
                serverChannel = b.bind(properties.getBindAddress(), properties.getPort())
                        .sync()
                        .channel();
                log.info("Netty WebSocket server started on {}:{}",
                        properties.getBindAddress(), properties.getPort());
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Netty WebSocket server thread interrupted");
            } catch (Exception e) {
                log.error("Netty WebSocket server failed to start: code={}, type={}",
                        ResponseCode.SERVICE_ERROR.getCode(), exceptionType(e));
            }
        }, "feibo-websocket-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @PreDestroy
    public void destroy() {
        Channel channel = serverChannel;
        if (channel != null) {
            channel.close();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("Netty WebSocket server stopped");
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }
}
