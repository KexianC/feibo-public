package com.hzmwmt.infrastructure.gateway.ai;

import com.alibaba.fastjson.JSON;
import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import com.hzmwmt.domain.ai.service.chat.ChatPricing;
import com.hzmwmt.infrastructure.gateway.ai.openai.ChatUsageCapture;
import com.hzmwmt.infrastructure.gateway.ai.openai.IOpenAIChatService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

/** One provider failure may switch to DeepSeek once, inside the same logical chat request. */
@Service
@Slf4j
public class ChatFailoverService {
    private final DatabaseBackedChatModelRegistry registry;
    private final IOpenAIChatService chatService;
    private final ApplicationEventPublisher events;
    // A cancelled JDBC call may ignore interruption and occupy its original worker.
    // Give recovery its own bounded workers so it cannot queue behind the failed attempt.
    private final Scheduler fallbackScheduler = Schedulers.newBoundedElastic(4, 256, "deepseek-fallback");

    @Value("${ai.chat.fallback.attempt-timeout-millis:45000}")
    private long attemptTimeoutMillis = 45000;

    public ChatFailoverService(DatabaseBackedChatModelRegistry registry,
                               IOpenAIChatService chatService, ApplicationEventPublisher events) {
        this.registry = registry;
        this.chatService = chatService;
        this.events = events;
    }

    @PreDestroy
    public void close() {
        fallbackScheduler.dispose();
    }

    public Disposable stream(ApplicationAIAggregate request) {
        return streamInternal(request, attempt -> openAi(attempt,
                registry.resolve(attempt.request().getAiUserMessageEntity().getAiModelConfigId(),
                        attempt.request().getModelCode())));
    }

    /** The callback adapter also supports providers that do not implement Spring AI ChatModel. */
    public Disposable stream(ApplicationAIAggregate request,
                             Function<ApplicationAIAggregate, Disposable> primary) {
        return streamInternal(request, attempt -> primary.apply(attempt.request()));
    }

    private Disposable streamInternal(ApplicationAIAggregate request,
                                      Function<AttemptContext, Disposable> primary) {
        String requestId = UUID.randomUUID().toString();
        AtomicReference<Throwable> originalError = new AtomicReference<>();
        AtomicReference<String> fallbackName = new AtomicReference<>();
        AtomicBoolean notified = new AtomicBoolean();
        AtomicBoolean terminal = new AtomicBoolean();

        java.util.function.Consumer<String> notify = outcome -> {
            Throwable failure = originalError.get();
            if (failure == null || !notified.compareAndSet(false, true)) return;
            try {
                events.publishEvent(new ModelFailureEvent(requestId, request.getUserId(),
                        request.getAiUserMessageEntity().getAiConversationId(),
                        request.getAiUserMessageEntity().getAiModelConfigId(),
                        request.getModelCode(), request.getModelName(), safeErrorType(failure),
                        "PRIMARY_FAILED".equals(outcome) ? null : "deepseek", fallbackName.get(), outcome));
            } catch (RuntimeException notificationFailure) {
                log.error("Model failure notice could not be queued; requestId={}, errorType={}",
                        requestId, notificationFailure.getClass().getSimpleName());
            }
        };

        Flux<Chunk> answers = attempt(request, request.getModelCode(), request.getModelName(), primary,
                        Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    if (isContentRejection(error)) return Flux.error(error);
                    originalError.set(error);
                    if ("deepseek".equals(request.getModelCode())) {
                        notify.accept("PRIMARY_FAILED");
                        return Flux.error(error);
                    }
                    return Flux.defer(() -> {
                        DatabaseBackedChatModelRegistry.FallbackModel fallback;
                        try {
                            fallback = registry.resolveDeepSeekFallback(
                                    request.getAiUserMessageEntity().getAiModelConfigId());
                            fallbackName.set(fallback.modelName());
                        } catch (RuntimeException unavailable) {
                            notify.accept("FALLBACK_UNAVAILABLE");
                            return Flux.<Chunk>error(error);
                        }
                        Chunk switched = new Chunk("model_fallback", JSON.toJSONString(Map.of(
                                "provider", "deepseek", "model", fallback.modelName())), false, null);
                        return Flux.concat(Flux.just(switched),
                                        attempt(request, "deepseek", fallback.modelName(),
                                                next -> openAi(next, fallback.chatModel()), fallbackScheduler))
                                .doOnNext(chunk -> {
                                    if (chunk.complete()) notify.accept("FALLBACK_SUCCEEDED");
                                })
                                .doOnError(ignored -> notify.accept("FALLBACK_FAILED"));
                    }).subscribeOn(fallbackScheduler);
                });

        // Persistence/SSE callbacks are downstream of failover. Their failures cannot call another model.
        return answers.doOnCancel(() -> notify.accept("CANCELLED")).subscribe(chunk -> {
            if (terminal.get()) return;
            if (chunk.complete()) {
                terminal.set(true);
                try {
                    request.getAiUserMessageEntity().setUsageRecord(chunk.usage());
                    request.getOnCompleteWithResult().accept(chunk.text());
                } catch (RuntimeException callbackFailure) {
                    request.getOnError().accept(callbackFailure);
                }
            } else {
                request.getOnNext().accept(chunk.type(), chunk.text());
            }
        }, error -> {
            if (terminal.compareAndSet(false, true)) request.getOnError().accept(error);
        });
    }

    private Flux<Chunk> attempt(ApplicationAIAggregate request, String provider, String model,
                                Function<AttemptContext, Disposable> starter, Scheduler scheduler) {
        return Flux.<Chunk>create(sink -> {
            Disposable.Swap subscription = Disposables.swap();
            sink.onDispose(subscription);
            AtomicBoolean completed = new AtomicBoolean();
            AtomicReference<ChatUsageRecord> usage = new AtomicReference<>();
            ApplicationAIAggregate adapted = ApplicationAIAggregate.builder()
                    .userId(request.getUserId()).sysWords(request.getSysWords())
                    .modelCode(provider).modelName(model)
                    .aiUserMessageEntity(request.getAiUserMessageEntity())
                    .aiMessageEntityHistoryList(request.getAiMessageEntityHistoryList())
                    .onNext((type, text) -> {
                        if (!sink.isCancelled() && !completed.get()) sink.next(new Chunk(type, text, false, null));
                    })
                    .onError(error -> {
                        if (completed.compareAndSet(false, true)) sink.error(error);
                    })
                    .onCompleteWithResult(result -> {
                        if (completed.compareAndSet(false, true) && !sink.isCancelled()) {
                            if (result == null || result.isBlank()) {
                                sink.error(new EmptyModelResponseException());
                            } else {
                                sink.next(new Chunk(null, result, true, usage.get()));
                                sink.complete();
                            }
                        }
                    }).build();
            if (sink.isCancelled()) return;
            try {
                subscription.update(starter.apply(new AttemptContext(adapted,
                        () -> !sink.isCancelled() && !completed.get(), usage)));
            } catch (RuntimeException failure) {
                if (completed.compareAndSet(false, true)) sink.error(failure);
            }
        }).subscribeOn(scheduler)
                .timeout(Duration.ofMillis(Math.max(1, attemptTimeoutMillis)));
    }

    private Disposable openAi(AttemptContext attempt, ChatModel model) {
        // Resolution can block in JDBC even after timeout/cancel. Do not launch that stale request.
        if (!attempt.active().getAsBoolean()) return Disposables.disposed();
        ApplicationAIAggregate request = attempt.request();
        ChatUsageRecord usage = new ChatUsageRecord();
        usage.setProvider(request.getModelCode());
        usage.setModel(request.getModelName());
        usage.setRequestedAt(Instant.now().toString());
        attempt.usage().set(usage);
        List<Message> messages = new ArrayList<>();
        String persona = request.getSysWords();
        if (persona == null || persona.isBlank()) persona = "专业的AI医疗助手";
        messages.add(new SystemMessage("你是一位" + persona + "。请使用Markdown格式来组织和呈现你的回答。"));
        if (request.getAiMessageEntityHistoryList() != null) {
            request.getAiMessageEntityHistoryList().forEach(history -> messages.add(
                    history.getAiChatRoleEnumVO().getCode()
                            ? new AssistantMessage(history.getContent()) : new UserMessage(history.getContent())));
        }
        messages.add(new UserMessage(request.getAiUserMessageEntity().getContent()));
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .model(request.getModelName()).streamUsage(true).build());
        if (!attempt.active().getAsBoolean()) return Disposables.disposed();
        return chatService.flowGenerate(prompt, model, request.getOnNext(), request.getOnError(), result -> {
            if (!attempt.active().getAsBoolean()) return;
            try {
                ChatPricing.price(usage, registry.endpointHost(request.getModelCode()));
            } catch (RuntimeException pricingFailure) {
                usage.setEstimatedCost(null);
                usage.setCostStatus("PRICE_UNCONFIRMED");
                log.warn("Fallback chat pricing unavailable; errorType={}", pricingFailure.getClass().getSimpleName());
            }
            request.getOnCompleteWithResult().accept(result);
        }, response -> {
            if (attempt.active().getAsBoolean()) ChatUsageCapture.capture(usage, response);
        });
    }

    private static boolean isContentRejection(Throwable error) {
        return error instanceof AppException app
                && ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode().equals(app.getCode())
                && "消息命中敏感内容，发送失败".equals(app.getInfo());
    }

    private static String safeErrorType(Throwable error) {
        // Never persist provider response bodies, URLs, prompt contents, or credential-bearing messages.
        return error.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "");
    }

    private record AttemptContext(ApplicationAIAggregate request, BooleanSupplier active,
                                  AtomicReference<ChatUsageRecord> usage) {}
    private record Chunk(String type, String text, boolean complete, ChatUsageRecord usage) {}
    private static final class EmptyModelResponseException extends RuntimeException {}
}
