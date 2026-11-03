package com.hzmwmt.infrastructure.gateway.netty.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hzmwmt.domain.callback.ASRCallback;
import com.hzmwmt.domain.service.asr.impl.DashScopeASRService;
import com.hzmwmt.infrastructure.dao.PushMessageMapper;
import com.hzmwmt.infrastructure.dao.po.PushMessage;
import com.hzmwmt.infrastructure.gateway.netty.INettyService;
import com.hzmwmt.types.util.SpringUtil;
import com.hzmwmt.types.util.TimeAgoUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 最终整合版 Handler：同时支持 业务消息推送 + 语音识别
 */
@Slf4j
// 1. 泛型必须是 WebSocketFrame，这样才能同时收 文本 和 二进制
public class NettyServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final String ASR_ERROR_MESSAGE = "语音识别失败，请稍后重试";
    private static final long ASR_SILENCE_TIMEOUT_MS = 30_000;
    private static final long ASR_MAX_DURATION_MS = 5 * 60_000;
    private static final long ASR_FINISH_TIMEOUT_MS = 3_000;
    private static final long ASR_MAX_PCM_BYTES = 16_000L * 2 * 5 * 60;
    private static final int ASR_PRE_ROLL_BYTES = 16_000 * 2 * 300 / 1000;
    private static final int ASR_MAX_FRAME_BYTES = 64 * 1024;

    private final ApplicationContext applicationContext;
    private final LongSupplier monotonicClock;
    // Session state is confined to the channel event loop, including provider callbacks.
    private AsrSession asrSession;
    private boolean sessionProtocol;
    // A legacy stream has no frame/session ID. After a server-enforced limit,
    // continuous bytes cannot prove a new user recording: require a control
    // boundary before allowing the next implicit legacy start.
    private boolean legacyRestartLocked;
    private final LinkedHashSet<String> closedSessionIds = new LinkedHashSet<>();

    public NettyServerHandler(ApplicationContext applicationContext) {
        this(applicationContext, System::nanoTime);
    }

    NettyServerHandler(ApplicationContext applicationContext, LongSupplier monotonicClock) {
        this.applicationContext = applicationContext;
        this.monotonicClock = monotonicClock;
    }

    /**
     * 连接建立时：只做日志，不要启动 ASR！
     * 避免纯业务用户占用 ASR 资源。
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("WebSocket 连接建立: {}", ctx.channel().id());
    }

    /**
     * 连接断开时：清理所有资源
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 1. 如果开启过 ASR，就停止它
        cancelCurrentAsrSession(ctx, "connection_closed", false);
        // 2. 解绑用户 (原有逻辑)
        UserChannelManager.unbind(ctx.channel());

        super.channelInactive(ctx);
        log.info("WebSocket 连接断开: {}", ctx.channel().id());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (ctx.channel().attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).get() == null) {
            closePolicyViolation(ctx, "authentication required");
            return;
        }
        // --- 场景 A: 文本消息 (登录 / 业务指令) ---
        if (frame instanceof TextWebSocketFrame) {
            handleTextFrame(ctx, (TextWebSocketFrame) frame);
        }

        // --- 场景 B: 二进制消息 (语音流) ---
        else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryFrame(ctx, (BinaryWebSocketFrame) frame);
        }
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        try {
            JsonObject json = JsonParser.parseString(msg.text()).getAsJsonObject();
            if (!json.has("type")) return;

            String type = json.get("type").getAsString();
            Long authenticatedUserId = ctx.channel()
                    .attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID)
                    .get();

            if ("login".equals(type)) {
                // Legacy clients may still send this frame, but identity always
                // comes from the one-time handshake ticket.
                if (json.has("userId")
                        && !String.valueOf(authenticatedUserId).equals(json.get("userId").getAsString())) {
                    closePolicyViolation(ctx, "user identity mismatch");
                    return;
                }
                sendJson(ctx, "login", "authenticated");
            } else if ("ping".equals(type)) {
                sendJson(ctx, "pong", "");
            } else if ("start_asr".equals(type)) {
                startAsrSession(ctx, readSessionId(json));
            } else if ("stop_asr".equals(type) || "cancel_asr".equals(type)) {
                String sessionId = readSessionId(json);
                if (sessionId == null && !sessionProtocol) legacyRestartLocked = false;
                AsrSession session = matchingSession(sessionId);
                if (session == null) {
                    // A late old stop must never release a new recording.
                    sendAsr(ctx, sessionId, "asr_complete", "", "not_active");
                } else if ("cancel_asr".equals(type)) {
                    completeAsrSession(ctx, session, "cancelled", true);
                } else {
                    finishAsrSession(ctx, session, "stopped");
                }
            }
        } catch (Exception e) {
            log.warn("Invalid WebSocket text command from authenticated channel: type={}",
                    exceptionType(e));
            closePolicyViolation(ctx, "invalid command");
        }
    }

    /** Audio never implicitly reopens an identified session after stop/cancel. */
    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) {
        AsrSession session = asrSession;
        if (session == null && !sessionProtocol && !legacyRestartLocked) session = openAsrSession(ctx, null);
        if (session == null || !session.acceptingAudio) return;
        long now = monotonicClock.getAsLong();
        // Scheduled tasks may be delayed under load; inbound audio also checks
        // the monotonic deadlines before it can renew activity or reach ASR.
        if (now - session.startedNanos >= TimeUnit.MILLISECONDS.toNanos(ASR_MAX_DURATION_MS)) {
            finishAsrSession(ctx, session, "max_duration");
            return;
        }
        if (now - session.lastActivityNanos >= TimeUnit.MILLISECONDS.toNanos(ASR_SILENCE_TIMEOUT_MS)) {
            finishAsrSession(ctx, session, "silence_timeout");
            return;
        }

        ByteBuf content = msg.content();
        int length = content.readableBytes();
        if (length == 0) return;
        if ((length & 1) != 0 || length > ASR_MAX_FRAME_BYTES) {
            sendAsr(ctx, session.id, "asr_error", "语音格式无效，请重新录音", "invalid_audio");
            completeAsrSession(ctx, session, "invalid_audio", true);
            return;
        }
        if (session.receivedPcmBytes + length > ASR_MAX_PCM_BYTES) {
            finishAsrSession(ctx, session, "max_duration");
            return;
        }
        byte[] bytes = new byte[length];
        content.readBytes(bytes);
        session.receivedPcmBytes += length;
        boolean activity = session.activity.observe(bytes);
        if (activity) session.lastActivityNanos = monotonicClock.getAsLong();

        if (session.service == null) {
            if (!activity) {
                retainPreRoll(session, bytes);
                return;
            }
            initASRService(ctx, session);
            if (asrSession != session || session.service == null) return;
        }
        try {
            for (byte[] prefix : session.preRoll) session.service.sendAudioFrame(prefix);
            session.preRoll.clear();
            session.preRollBytes = 0;
            session.service.sendAudioFrame(bytes);
        } catch (Exception e) {
            handleAsrError(ctx, session, e);
        }
    }

    private String readSessionId(JsonObject json) {
        if (!json.has("sessionId") || json.get("sessionId").isJsonNull()) return null;
        if (!json.get("sessionId").isJsonPrimitive()
                || !json.get("sessionId").getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("invalid ASR session id");
        }
        String id = json.get("sessionId").getAsString();
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid ASR session id");
        }
        return id;
    }

    private void startAsrSession(ChannelHandlerContext ctx, String id) {
        if (id != null) sessionProtocol = true;
        if (sessionProtocol && id == null) {
            sendAsr(ctx, null, "asr_error", "请重新开始语音输入", "session_required");
            return;
        }
        // Explicit start is also a fresh intent boundary; ordinary legacy
        // clients instead send stop_asr before starting their next capture.
        if (id == null) legacyRestartLocked = false;
        if (id != null && closedSessionIds.contains(id)) {
            sendAsr(ctx, id, "asr_error", "请重新开始语音输入", "session_closed");
            sendAsr(ctx, id, "asr_complete", "", "session_closed");
            return;
        }
        AsrSession current = matchingSession(id);
        if (current != null) {
            sendAsr(ctx, current.id, current.acceptingAudio ? "asr_started" : "asr_stopping",
                    "", current.finishReason);
            return;
        }
        cancelCurrentAsrSession(ctx, "replaced", true);
        AsrSession session = openAsrSession(ctx, id);
        sendAsr(ctx, session.id, "asr_started", "", null);
    }

    private AsrSession openAsrSession(ChannelHandlerContext ctx, String id) {
        AsrSession session = new AsrSession(id, monotonicClock.getAsLong());
        asrSession = session;
        session.maxTimer = ctx.executor().schedule(
                () -> finishAsrSession(ctx, session, "max_duration"),
                ASR_MAX_DURATION_MS, TimeUnit.MILLISECONDS);
        scheduleSilenceCheck(ctx, session, ASR_SILENCE_TIMEOUT_MS);
        return session;
    }

    private AsrSession matchingSession(String id) {
        if (asrSession == null) return null;
        return asrSession.id == null ? (id == null ? asrSession : null)
                : asrSession.id.equals(id) ? asrSession : null;
    }

    private void scheduleSilenceCheck(ChannelHandlerContext ctx, AsrSession session, long delayMs) {
        session.silenceTimer = ctx.executor().schedule(() -> {
            if (asrSession != session || !session.acceptingAudio) return;
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    Math.max(0, monotonicClock.getAsLong() - session.lastActivityNanos));
            long remainingMs = ASR_SILENCE_TIMEOUT_MS - elapsedMs;
            if (remainingMs <= 0) finishAsrSession(ctx, session, "silence_timeout");
            else scheduleSilenceCheck(ctx, session, remainingMs);
        }, Math.max(1, delayMs), TimeUnit.MILLISECONDS);
    }

    private void retainPreRoll(AsrSession session, byte[] bytes) {
        session.preRoll.addLast(bytes);
        session.preRollBytes += bytes.length;
        while (session.preRollBytes > ASR_PRE_ROLL_BYTES) {
            byte[] first = session.preRoll.removeFirst();
            int excess = session.preRollBytes - ASR_PRE_ROLL_BYTES;
            if (first.length > excess) {
                session.preRoll.addFirst(Arrays.copyOfRange(first, excess, first.length));
                session.preRollBytes -= excess;
            } else session.preRollBytes -= first.length;
        }
    }

    /** Only PCM-confirmed activity starts the supplier; initially silent sessions stay local. */
    private void initASRService(ChannelHandlerContext ctx, AsrSession session) {
        log.info("检测到有效音频活动，正在初始化 ASR 服务...");
        try {
            session.service = applicationContext.getBean(DashScopeASRService.class);
            session.service.start(new ASRCallback() {
                @Override
                public void onPartialResult(String text) {
                    onSessionEvent(ctx, session, () -> sendAsrText(ctx, session, "asr_partial", text));
                }
                @Override
                public void onFinalResult(String text) {
                    onSessionEvent(ctx, session, () -> sendAsrText(ctx, session, "asr_final", text));
                }
                @Override
                public void onError(Exception e) {
                    onSessionEvent(ctx, session, () -> handleAsrError(ctx, session, e));
                }
                @Override
                public void onComplete() {
                    onSessionEvent(ctx, session, () -> completeAsrSession(ctx, session,
                            session.finishReason != null ? session.finishReason : "provider_complete", true));
                }
            });
        } catch (Exception e) {
            handleAsrError(ctx, session, e);
        }
    }

    private void onSessionEvent(ChannelHandlerContext ctx, AsrSession session, Runnable action) {
        Runnable guarded = () -> {
            if (asrSession == session && ctx.channel().isActive()) action.run();
        };
        if (ctx.executor().inEventLoop()) guarded.run();
        else if (!ctx.executor().isShuttingDown()) ctx.executor().execute(guarded);
    }

    private void sendAsrText(ChannelHandlerContext ctx, AsrSession session, String type, String text) {
        if (text != null && !text.isBlank()) sendAsr(ctx, session.id, type, text, null);
    }

    private void handleAsrError(ChannelHandlerContext ctx, AsrSession session, Exception error) {
        if (asrSession != session) return;
        log.error("ASR 服务处理失败: type={}", exceptionType(error));
        sendAsr(ctx, session.id, "asr_error", ASR_ERROR_MESSAGE, "provider_error");
        completeAsrSession(ctx, session, "provider_error", true);
    }

    private void finishAsrSession(ChannelHandlerContext ctx, AsrSession session, String reason) {
        if (asrSession != session || !session.acceptingAudio) return;
        session.acceptingAudio = false;
        session.finishReason = reason;
        if (session.id == null && ("silence_timeout".equals(reason) || "max_duration".equals(reason))) {
            legacyRestartLocked = true;
        }
        cancelTimer(session.silenceTimer);
        cancelTimer(session.maxTimer);
        if (session.id != null) sendAsr(ctx, session.id, "asr_stopping", "", reason);
        if (session.service == null) {
            completeAsrSession(ctx, session, reason, true);
            return;
        }
        session.finishTimer = ctx.executor().schedule(
                () -> completeAsrSession(ctx, session, "finish_timeout", true),
                ASR_FINISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            session.service.finish();
        } catch (Exception e) {
            handleAsrError(ctx, session, e);
        }
    }

    private void cancelCurrentAsrSession(ChannelHandlerContext ctx, String reason, boolean notify) {
        if (asrSession != null) completeAsrSession(ctx, asrSession, reason, notify);
    }

    private void completeAsrSession(ChannelHandlerContext ctx, AsrSession session, String reason, boolean notify) {
        if (asrSession != session) return;
        asrSession = null;
        session.acceptingAudio = false;
        cancelTimer(session.silenceTimer);
        cancelTimer(session.maxTimer);
        cancelTimer(session.finishTimer);
        session.preRoll.clear();
        if (session.id != null) {
            closedSessionIds.add(session.id);
            if (closedSessionIds.size() > 256) closedSessionIds.remove(closedSessionIds.iterator().next());
        }
        if (session.service != null) {
            try {
                session.service.stop();
            } catch (Exception e) {
                log.warn("ASR 服务停止失败: type={}", exceptionType(e));
            }
        }
        if (notify) sendAsr(ctx, session.id, "asr_complete", "", reason);
    }

    private static void cancelTimer(ScheduledFuture<?> timer) {
        if (timer != null) timer.cancel(false);
    }

    private static final class AsrSession {
        final String id;
        final AsrPcmActivity activity = new AsrPcmActivity();
        final ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
        DashScopeASRService service;
        boolean acceptingAudio = true;
        int preRollBytes;
        long receivedPcmBytes;
        final long startedNanos;
        long lastActivityNanos;
        String finishReason;
        ScheduledFuture<?> silenceTimer;
        ScheduledFuture<?> maxTimer;
        ScheduledFuture<?> finishTimer;

        AsrSession(String id, long now) {
            this.id = id;
            startedNanos = now;
            lastActivityNanos = now;
        }
    }

    private void handleOfflineMessages(Long userId) {
        PushMessageMapper pushMessageMapper = SpringUtil.getBean(PushMessageMapper.class);
        INettyService nettyService = SpringUtil.getBean(INettyService.class);
        List<PushMessage> unreadList = pushMessageMapper.selectUnreadListByUserId(userId);

        if (unreadList != null && !unreadList.isEmpty()) {
            for (PushMessage message : unreadList) {
                if (nettyService.sendMessageToUser(userId, message.getTitle(), message.getContent(), TimeAgoUtil.format(message.getCreateTime()))) {
                    pushMessageMapper.updatePushMessage2Read(message.getPushMessageId());
                }
            }
        }
    }

    private void sendJson(ChannelHandlerContext ctx, String type, String content) {
        sendAsr(ctx, null, type, content, null);
    }

    private void sendAsr(ChannelHandlerContext ctx, String sessionId, String type, String content, String reason) {
        if (ctx.channel().isActive()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", type);
            payload.addProperty("content", content != null ? content : "");
            if (sessionId != null) payload.addProperty("sessionId", sessionId);
            if (reason != null) payload.addProperty("reason", reason);
            ctx.writeAndFlush(new TextWebSocketFrame(payload.toString()));
        }
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            Long userId = ctx.channel()
                    .attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID)
                    .get();
            if (userId == null || userId <= 0) {
                closePolicyViolation(ctx, "authentication required");
                return;
            }
            UserChannelManager.bind(userId, ctx.channel());
            handleOfflineMessages(userId);
            log.info("Authenticated WebSocket channel established for user {}", userId);
            return;
        }
        if (event instanceof IdleStateEvent) {
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, event);
    }

    private void closePolicyViolation(ChannelHandlerContext ctx, String reason) {
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(new CloseWebSocketFrame(1008, reason))
                    .addListener(future -> ctx.close());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 异常: type={}", exceptionType(cause));
        cancelCurrentAsrSession(ctx, "connection_error", false);
        UserChannelManager.unbind(ctx.channel());
        ctx.close();
    }
}
