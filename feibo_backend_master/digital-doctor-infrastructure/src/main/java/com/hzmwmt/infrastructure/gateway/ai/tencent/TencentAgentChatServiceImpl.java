package com.hzmwmt.infrastructure.gateway.ai.tencent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ChatRequestDTO;
import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ReplyEventDTO;
import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ThoughtEventDTO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TencentAgentChatServiceImpl
 * @Date: 2025/9/12 10:40
 */
@Slf4j
@Service
public class TencentAgentChatServiceImpl implements ITencentAgentChatService {
    @Qualifier("tencentAgentWebClient")
    @Resource
    private WebClient tencentAgentWebClient;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Disposable flowGenerate(ChatRequestDTO request, TencentSseCallback callback) {
        log.info("开始创建 WebClient SSE 连接");

        StringBuilder fullResponse = new StringBuilder();
        try {
            // 使用 WebClient 发起 SSE 请求
            Flux<ServerSentEvent<String>> eventStream = tencentAgentWebClient
                    .post()
                    .uri("/v1/qbot/chat/sse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                    })
                    .timeout(Duration.ofMinutes(10));
            AtomicBoolean isFirstMessage = new AtomicBoolean(true);
            //订阅事件流
            return eventStream.subscribe(
                    // onNext: 处理每个 SSE 事件
                    event -> {
                        try {
                            String eventType = event.event();
                            String eventData = event.data();


                            if ("reply".equals(eventType)) {
                                ReplyEventDTO replyEvent = objectMapper.readValue(eventData, ReplyEventDTO.class);
                                ReplyEventDTO.ReplyPayload payload = replyEvent.getPayload();
                                if (eventData != null) {
                                    // 检查敏感内容
                                    if (payload.isEvil()) {
                                        log.warn("消息命中敏感内容，请求失败: code={}",
                                                ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode());
                                        callback.onError(new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(),
                                                "消息命中敏感内容，发送失败"));
                                        return;
                                    }

                                    String content = payload.getContent();

                                    // 检查并发限制
                                    if ("超出并发数限制".equals(content)) {
                                        log.warn("腾讯云智能体并发超限: code={}",
                                                ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode());
                                        callback.onError(new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(),
                                                "超出并发数限制"));
                                        return;
                                    }

                                    if (isFirstMessage.compareAndSet(true, false)) {
                                        log.debug("已跳过腾讯智能体的首条占位消息");
                                        return;
                                    }

                                    callback.onReply(eventType, content);
                                    fullResponse.append(content);

                                }
                            } else if ("thought".equals(eventType)) {

                                ThoughtEventDTO thoughtEvent = objectMapper.readValue(eventData, ThoughtEventDTO.class);
                                if (thoughtEvent.getPayload() != null &&
                                        thoughtEvent.getPayload().getProcedures() != null &&
                                        !thoughtEvent.getPayload().getProcedures().isEmpty()) {

                                    ThoughtEventDTO.Procedure firstProcedure = thoughtEvent.getPayload().getProcedures().get(0);
                                    if (firstProcedure != null && firstProcedure.getDebugging() != null) {
                                        String thoughtContent = firstProcedure.getDebugging().getContent();

                                        if (thoughtContent != null) {
                                            // 将提取到的思考内容封装并发送给回调
                                            callback.onReply(eventType, thoughtContent);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("解析 SSE 事件失败: code={}, type={}",
                                    ResponseCode.INTERNET_EXCEPTION.getCode(), exceptionType(e));
                            callback.onError(new AppException(ResponseCode.INTERNET_EXCEPTION.getCode(),
                                    ResponseCode.INTERNET_EXCEPTION.getInfo()));
                        }
                    },
                    // onError: 处理流错误
                    error -> {
                        log.error("WebClient SSE 连接失败: code={}, type={}",
                                ResponseCode.INTERNET_EXCEPTION.getCode(), exceptionType(error));
                        callback.onError(new AppException(ResponseCode.INTERNET_EXCEPTION.getCode(),
                                ResponseCode.INTERNET_EXCEPTION.getInfo()));
                    },
                    // onComplete: 流完成处理
                    () -> {
                        log.info("WebClient SSE 连接已完成 - 总响应长度: {}", fullResponse.length());
                        callback.onComplete(fullResponse.toString());
                    }
            );

        } catch (Exception e) {
            log.error("创建 WebClient SSE 连接失败: code={}, type={}",
                    ResponseCode.INTERNET_EXCEPTION.getCode(), exceptionType(e));
            callback.onError(new AppException(ResponseCode.INTERNET_EXCEPTION.getCode(),
                    ResponseCode.INTERNET_EXCEPTION.getInfo()));
            // 异常情况下返回一个已处置的 Disposable
            return Disposables.disposed();
        }
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }
}
