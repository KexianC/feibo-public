package com.hzmwmt.security;

import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import com.hzmwmt.infrastructure.gateway.ai.ChatFailoverService;
import com.hzmwmt.infrastructure.gateway.ai.DatabaseBackedChatModelRegistry;
import com.hzmwmt.infrastructure.gateway.ai.openai.IOpenAIChatService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Callback-driven tests exercise failover without contacting a model provider. */
class ChatFailoverServiceTest {
    private static final long PRIMARY_ID = 9005300000000000001L;
    private static final long FALLBACK_ID = 9005300000000000002L;

    private final ChatModel primaryModel = mock(ChatModel.class);
    private final ChatModel fallbackModel = mock(ChatModel.class);
    private final List<ModelFailureEvent> alerts = new CopyOnWriteArrayList<>();
    private DatabaseBackedChatModelRegistry registry;
    private RecordingChatService provider;
    private ChatFailoverService service;
    private Delivery delivery;
    private ApplicationAIAggregate request;

    @BeforeEach
    void setUp() {
        registry = mock(DatabaseBackedChatModelRegistry.class);
        provider = new RecordingChatService();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> {
            alerts.add(invocation.getArgument(0));
            return null;
        }).when(publisher).publishEvent(any(ModelFailureEvent.class));
        service = new ChatFailoverService(registry, provider, publisher);
        delivery = new Delivery();
        request = ApplicationAIAggregate.builder()
                .userId(11L)
                .modelCode("qwen")
                .modelName("qwen3.7-flash")
                .sysWords("Keep the original system instruction.")
                .aiUserMessageEntity(AIMessageEntity.builder()
                        .aiMessageId(31L).aiConversationId(21L)
                        .aiModelConfigId(PRIMARY_ID)
                        .aiChatRoleEnumVO(AIChatRoleEnumVO.USER)
                        .content("Current user question").build())
                .aiMessageEntityHistoryList(List.of(
                        AIMessageEntity.builder().aiChatRoleEnumVO(AIChatRoleEnumVO.USER)
                                .content("Previous user question").build(),
                        AIMessageEntity.builder().aiChatRoleEnumVO(AIChatRoleEnumVO.ASSISTANT)
                                .content("Previous assistant answer").build()))
                .onNext(delivery::next)
                .onError(delivery::error)
                .onCompleteWithResult(delivery::complete)
                .build();
        when(registry.resolve(PRIMARY_ID, "qwen")).thenReturn(primaryModel);
        when(registry.resolveDeepSeekFallback(PRIMARY_ID)).thenReturn(
                new DatabaseBackedChatModelRegistry.FallbackModel(
                        FALLBACK_ID, "deepseek-v4-flash", fallbackModel));
        when(registry.endpointHost("qwen")).thenReturn("dashscope.aliyuncs.com");
        when(registry.endpointHost("deepseek")).thenReturn("api.deepseek.com");
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void successfulPrimaryDoesNotResolveFallbackOrPublishFailure() throws Exception {
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.reply("Primary answer");
        primary.complete("Primary answer");

        delivery.awaitTerminal();
        assertThat(delivery.results).containsExactly("Primary answer");
        assertThat(delivery.errors).isEmpty();
        assertThat(delivery.events).extracting(Event::type).containsExactly("reply");
        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        assertThat(alerts).isEmpty();
    }

    @Test
    void synchronousCompletionStillDisposesTheProviderSubscriptionReturnedAfterTheCallback() throws Exception {
        provider.onStart = attempt -> {
            attempt.reply("Immediate answer");
            attempt.complete("Immediate answer");
        };
        service.stream(request);
        Attempt primary = provider.takeAttempt();

        delivery.awaitTerminal();
        awaitCondition(primary.disposable::isDisposed);
        assertThat(delivery.results).containsExactly("Immediate answer");
        verify(registry, never()).resolveDeepSeekFallback(anyLong());
    }

    @Test
    void synchronousRegistryFailureStartsDeepseekAndPreservesConversationContext() throws Exception {
        when(registry.resolve(PRIMARY_ID, "qwen"))
                .thenThrow(new IllegalStateException("primary configuration unavailable"));

        service.stream(request);
        Attempt fallback = provider.takeAttempt();
        assertThat(fallback.model).isSameAs(fallbackModel);
        assertThat(fallback.prompt.getInstructions().get(0).getText())
                .contains("Keep the original system instruction.");
        assertThat(fallback.prompt.getInstructions().subList(1, 4)).extracting(Message::getText)
                .containsExactly("Previous user question", "Previous assistant answer", "Current user question");
        assertThat(fallback.prompt.getOptions().getModel()).isEqualTo("deepseek-v4-flash");
        fallback.reply("Recovered answer");
        fallback.complete("Recovered answer");

        delivery.awaitTerminal();
        assertThat(delivery.events).extracting(Event::type).containsExactly("model_fallback", "reply");
        assertThat(delivery.results).containsExactly("Recovered answer");
        assertThat(delivery.errors).isEmpty();
        awaitAlert("FALLBACK_SUCCEEDED");
        assertThat(alerts.get(0).originalModelConfigId()).isEqualTo(PRIMARY_ID);
        assertThat(alerts.get(0).originalProvider()).isEqualTo("qwen");
        assertThat(alerts.get(0).fallbackProvider()).isEqualTo("deepseek");
        assertThat(alerts.get(0).userId()).isEqualTo(11L);
        assertThat(alerts.get(0).conversationId()).isEqualTo(21L);
    }

    @Test
    void synchronousProviderStartupFailureAlsoStartsFallbackOnce() throws Exception {
        provider.onStart = attempt -> {
            if (attempt.model == primaryModel) {
                throw new IllegalStateException("stream startup failed");
            }
        };
        service.stream(request);
        assertThat(provider.takeAttempt().model).isSameAs(primaryModel);
        Attempt fallback = provider.takeAttempt();
        fallback.reply("Recovered answer");
        fallback.complete("Recovered answer");

        delivery.awaitTerminal();
        assertThat(delivery.results).containsExactly("Recovered answer");
        verify(registry, times(1)).resolveDeepSeekFallback(PRIMARY_ID);
        awaitAlert("FALLBACK_SUCCEEDED");
    }

    @Test
    void asynchronousFailureAfterPartialReplyReplacesTheFinalAnswer() throws Exception {
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.reply("Incomplete original answer");
        primary.fail(new IllegalStateException("upstream disconnected"));
        Attempt fallback = provider.takeAttempt();
        assertThat(fallback.model).isSameAs(fallbackModel);
        assertThat(fallback.prompt.getInstructions()).extracting(Message::getText)
                .doesNotContain("Incomplete original answer");
        fallback.reply("Replacement answer");
        fallback.complete("Replacement answer");

        delivery.awaitTerminal();
        assertThat(delivery.events).extracting(Event::type)
                .containsExactly("reply", "model_fallback", "reply");
        assertThat(delivery.results).containsExactly("Replacement answer");
        assertThat(delivery.errors).isEmpty();
        assertThat(primary.disposable.isDisposed()).isTrue();
        awaitAlert("FALLBACK_SUCCEEDED");
    }

    @Test
    void fallbackUsageKeepsOnlyTheLatestFallbackSnapshotAndIgnoresLatePrimaryUsage() throws Exception {
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.usage(usage(100, 50, 20));
        primary.fail(new IllegalStateException("upstream disconnected"));
        Attempt fallback = provider.takeAttempt();
        fallback.usage(usage(10, 5, 2));
        fallback.usage(usage(10, 5, 2));
        primary.usage(usage(1000, 500, 200));
        fallback.reply("Recovered answer");
        fallback.complete("Recovered answer");

        delivery.awaitTerminal();
        var usage = request.getAiUserMessageEntity().getUsageRecord();
        assertThat(usage.getProvider()).isEqualTo("deepseek");
        assertThat(usage.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(usage.getInputTokens()).isEqualTo(10L);
        assertThat(usage.getOutputTokens()).isEqualTo(5L);
        assertThat(usage.getTotalTokens()).isEqualTo(15L);
        assertThat(usage.getCachedTokens()).isEqualTo(2L);
        assertThat(usage.getUsageSource()).isEqualTo("PROVIDER");
    }

    @Test
    void emptyPrimaryResponseStartsFallback() throws Exception {
        service.stream(request);
        provider.takeAttempt().complete("   ");
        Attempt fallback = provider.takeAttempt();
        fallback.reply("Nonempty fallback answer");
        fallback.complete("Nonempty fallback answer");

        delivery.awaitTerminal();
        assertThat(delivery.results).containsExactly("Nonempty fallback answer");
        assertThat(delivery.errors).isEmpty();
        awaitAlert("FALLBACK_SUCCEEDED");
    }

    @Test
    void primaryAndFallbackFailureEndOnceWithoutAnotherRetry() throws Exception {
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.fail(new IllegalStateException("primary unavailable"));
        Attempt fallback = provider.takeAttempt();
        fallback.fail(new IllegalStateException("deepseek unavailable"));

        delivery.awaitTerminal();
        assertThat(delivery.errors).hasSize(1);
        assertThat(delivery.results).isEmpty();
        assertThat(provider.attempts).hasSize(2);
        verify(registry, times(1)).resolveDeepSeekFallback(PRIMARY_ID);
        awaitAlert("FALLBACK_FAILED");
        assertThat(alerts).hasSize(1);
    }

    @Test
    void failingOriginalDeepseekDoesNotLoopBackToDeepseek() throws Exception {
        request.setModelCode("deepseek");
        request.setModelName("deepseek-v4-flash");
        when(registry.resolve(PRIMARY_ID, "deepseek")).thenReturn(primaryModel);
        service.stream(request);
        provider.takeAttempt().fail(new IllegalStateException("deepseek unavailable"));

        delivery.awaitTerminal();
        assertThat(delivery.errors).hasSize(1);
        assertThat(provider.attempts).hasSize(1);
        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        awaitAlert("PRIMARY_FAILED");
    }

    @Test
    void unavailableFallbackProducesOneFinalErrorAndAlert() throws Exception {
        when(registry.resolveDeepSeekFallback(PRIMARY_ID)).thenReturn(null);
        service.stream(request);
        provider.takeAttempt().fail(new IllegalStateException("primary unavailable"));

        delivery.awaitTerminal();
        assertThat(delivery.errors).hasSize(1);
        assertThat(delivery.results).isEmpty();
        assertThat(provider.attempts).hasSize(1);
        awaitAlert("FALLBACK_UNAVAILABLE");
    }

    @Test
    void cancellationDisposesPrimaryAndLateProviderFailureCannotStartFallback() throws Exception {
        Disposable subscription = service.stream(request);
        Attempt primary = provider.takeAttempt();
        subscription.dispose();
        awaitCondition(primary.disposable::isDisposed);

        primary.fail(new IllegalStateException("late network failure after cancellation"));
        primary.reply("late reply");
        primary.complete("late result");

        assertThat(subscription.isDisposed()).isTrue();
        assertThat(delivery.events).isEmpty();
        assertThat(delivery.results).isEmpty();
        assertThat(delivery.errors).isEmpty();
        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        assertThat(provider.attempts).hasSize(1);
    }

    @Test
    void cancellationDuringFallbackDisposesItAndCannotDeliverLateAnswer() throws Exception {
        Disposable subscription = service.stream(request);
        provider.takeAttempt().fail(new IllegalStateException("primary unavailable"));
        Attempt fallback = provider.takeAttempt();
        subscription.dispose();
        awaitCondition(fallback.disposable::isDisposed);
        fallback.reply("late fallback reply");
        fallback.complete("late fallback result");

        assertThat(delivery.events).extracting(Event::type).containsExactly("model_fallback");
        assertThat(delivery.results).isEmpty();
        assertThat(delivery.errors).isEmpty();
        assertThat(provider.attempts).hasSize(2);
        awaitAlert("CANCELLED");
    }

    @Test
    void downstreamDeliveryFailureCannotTriggerASecondProviderCall() throws Exception {
        request.setOnNext((type, content) -> {
            throw new IllegalStateException("client disconnected during send");
        });
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.reply("Primary answer");
        awaitCondition(primary.disposable::isDisposed);

        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        assertThat(provider.attempts).hasSize(1);
        assertThat(delivery.results).isEmpty();
        assertThat(alerts).isEmpty();
    }

    @Test
    void disconnectWhileSendingFallbackNoticePreventsTheFallbackProviderFromStarting() throws Exception {
        request.setOnNext((type, content) -> {
            if ("model_fallback".equals(type)) {
                throw new IllegalStateException("client disconnected before fallback started");
            }
            delivery.next(type, content);
        });
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.fail(new IllegalStateException("primary unavailable"));

        delivery.awaitTerminal();
        awaitCondition(primary.disposable::isDisposed);
        assertThat(delivery.errors).hasSize(1);
        assertThat(provider.attempts).hasSize(1);
        assertThat(delivery.results).isEmpty();
        awaitAlert("CANCELLED");
    }

    @Test
    void completionCallbackFailureCannotTriggerFallback() throws Exception {
        CountDownLatch callbackInvoked = new CountDownLatch(1);
        request.setOnCompleteWithResult(content -> {
            callbackInvoked.countDown();
            throw new IllegalStateException("message persistence failed");
        });
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        primary.reply("Primary answer");
        primary.complete("Primary answer");
        assertThat(callbackInvoked.await(3, TimeUnit.SECONDS)).isTrue();
        awaitCondition(primary.disposable::isDisposed);

        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        assertThat(provider.attempts).hasSize(1);
        assertThat(alerts).isEmpty();
    }

    @Test
    void silentPrimaryTimesOutAndStartsDeepseek() throws Exception {
        ReflectionTestUtils.setField(service, "attemptTimeoutMillis", 250L);
        service.stream(request);
        Attempt primary = provider.takeAttempt();
        Attempt fallback = provider.takeAttempt();
        fallback.reply("Recovered after timeout");
        fallback.complete("Recovered after timeout");

        delivery.awaitTerminal();
        assertThat(primary.disposable.isDisposed()).isTrue();
        assertThat(delivery.results).containsExactly("Recovered after timeout");
        assertThat(delivery.errors).isEmpty();
        awaitAlert("FALLBACK_SUCCEEDED");
    }

    @Test
    void timedOutBlockedRegistryCannotLaunchStalePrimaryOrOverwriteFallbackUsage() throws Exception {
        ReflectionTestUtils.setField(service, "attemptTimeoutMillis", 500L);
        CountDownLatch resolutionEntered = new CountDownLatch(1);
        CountDownLatch releaseResolution = new CountDownLatch(1);
        CountDownLatch resolutionReturned = new CountDownLatch(1);
        when(registry.resolve(PRIMARY_ID, "qwen")).thenAnswer(invocation -> {
            resolutionEntered.countDown();
            boolean released = false;
            while (!released) {
                try {
                    releaseResolution.await();
                    released = true;
                } catch (InterruptedException ignored) {
                    // A JDBC driver may continue its lookup after the Reactor timeout interrupts it.
                }
            }
            resolutionReturned.countDown();
            return primaryModel;
        });

        Disposable subscription = service.stream(request);
        try {
            assertThat(resolutionEntered.await(3, TimeUnit.SECONDS)).isTrue();
            Attempt fallback = provider.takeAttempt();
            assertThat(fallback.model).isSameAs(fallbackModel);
            fallback.usage(usage(10, 5, 2));
            fallback.reply("Recovered while original lookup was blocked");
            assertThat(request.getAiUserMessageEntity().getUsageRecord()).isNull();

            releaseResolution.countDown();
            assertThat(resolutionReturned.await(3, TimeUnit.SECONDS)).isTrue();
            fallback.complete("Recovered while original lookup was blocked");
            delivery.awaitTerminal();

            assertThat(provider.pending.poll(300, TimeUnit.MILLISECONDS))
                    .as("the timed-out original lookup must not start another provider stream").isNull();
            assertThat(provider.attempts).hasSize(1);
            assertThat(delivery.results).containsExactly("Recovered while original lookup was blocked");
            assertThat(delivery.errors).isEmpty();
            var finalUsage = request.getAiUserMessageEntity().getUsageRecord();
            assertThat(finalUsage.getProvider()).isEqualTo("deepseek");
            assertThat(finalUsage.getModel()).isEqualTo("deepseek-v4-flash");
            assertThat(finalUsage.getInputTokens()).isEqualTo(10L);
            assertThat(finalUsage.getOutputTokens()).isEqualTo(5L);
            assertThat(finalUsage.getTotalTokens()).isEqualTo(15L);
            awaitAlert("FALLBACK_SUCCEEDED");
        } finally {
            releaseResolution.countDown();
            subscription.dispose();
        }
    }

    @Test
    void tencentContentRejectionIsDeliveredUnchangedWithoutFallbackOrFailureNotice() throws Exception {
        request.setModelCode("tencent");
        request.setModelName("doctor-agent");
        AppException refusal = new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(),
                "消息命中敏感内容，发送失败");
        TrackedDisposable tencentSubscription = new TrackedDisposable();
        service.stream(request, wrapped -> {
            wrapped.getOnError().accept(refusal);
            return tencentSubscription;
        });

        delivery.awaitTerminal();
        awaitCondition(tencentSubscription::isDisposed);
        assertThat(delivery.errors).containsExactly(refusal);
        assertThat(delivery.errors.get(0)).isSameAs(refusal);
        assertThat(delivery.results).isEmpty();
        assertThat(delivery.events).isEmpty();
        assertThat(provider.attempts).isEmpty();
        verify(registry, never()).resolveDeepSeekFallback(anyLong());
        assertThat(alerts).isEmpty();
    }

    @Test
    void customTencentPrimaryUsesTheSameFallbackAndOriginalConversation() throws Exception {
        request.setModelCode("tencent");
        request.setModelName("doctor-agent");
        BlockingQueue<ApplicationAIAggregate> primaryRequests = new LinkedBlockingQueue<>();
        TrackedDisposable tencentSubscription = new TrackedDisposable();
        service.stream(request, wrapped -> {
            primaryRequests.add(wrapped);
            return tencentSubscription;
        });
        ApplicationAIAggregate wrapped = primaryRequests.poll(3, TimeUnit.SECONDS);
        assertThat(wrapped).isNotNull();
        assertThat(wrapped.getAiUserMessageEntity()).isSameAs(request.getAiUserMessageEntity());
        assertThat(wrapped.getAiMessageEntityHistoryList()).isEqualTo(request.getAiMessageEntityHistoryList());
        wrapped.getOnError().accept(new IllegalStateException("Tencent unavailable"));
        Attempt fallback = provider.takeAttempt();
        assertThat(fallback.model).isSameAs(fallbackModel);
        fallback.reply("Recovered from Tencent failure");
        fallback.complete("Recovered from Tencent failure");

        delivery.awaitTerminal();
        assertThat(delivery.results).containsExactly("Recovered from Tencent failure");
        assertThat(tencentSubscription.isDisposed()).isTrue();
        awaitAlert("FALLBACK_SUCCEEDED");
        assertThat(alerts.get(0).originalProvider()).isEqualTo("tencent");
    }

    private void awaitAlert(String outcome) throws Exception {
        awaitCondition(() -> !alerts.isEmpty());
        assertThat(alerts.get(0).outcome()).isEqualTo(outcome);
    }

    private static void awaitCondition(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static ChatResponse usage(int input, int output, int cached) {
        var nativeUsage = new OpenAiApi.Usage(output, input, input + output,
                new OpenAiApi.Usage.PromptTokensDetails(null, cached), null, null, null);
        return new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(input, output, input + output, nativeUsage)).build());
    }

    private record Event(String type, String content) { }

    private static final class Delivery {
        private final List<Event> events = new CopyOnWriteArrayList<>();
        private final List<String> results = new CopyOnWriteArrayList<>();
        private final List<Throwable> errors = new CopyOnWriteArrayList<>();
        private final CountDownLatch terminal = new CountDownLatch(1);

        void next(String type, String content) { events.add(new Event(type, content)); }
        void complete(String result) { results.add(result); terminal.countDown(); }
        void error(Throwable error) { errors.add(error); terminal.countDown(); }
        void awaitTerminal() throws Exception {
            assertThat(terminal.await(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class TrackedDisposable implements Disposable {
        private final AtomicBoolean disposed = new AtomicBoolean();
        @Override public void dispose() { disposed.set(true); }
        @Override public boolean isDisposed() { return disposed.get(); }
    }

    private static final class Attempt {
        private final Prompt prompt;
        private final ChatModel model;
        private final BiConsumer<String, String> onNext;
        private final Consumer<Throwable> onError;
        private final Consumer<String> onComplete;
        private final Consumer<ChatResponse> onUsage;
        private final TrackedDisposable disposable = new TrackedDisposable();

        private Attempt(Prompt prompt, ChatModel model, BiConsumer<String, String> onNext,
                        Consumer<Throwable> onError, Consumer<String> onComplete,
                        Consumer<ChatResponse> onUsage) {
            this.prompt = prompt;
            this.model = model;
            this.onNext = onNext;
            this.onError = onError;
            this.onComplete = onComplete;
            this.onUsage = onUsage;
        }
        void reply(String text) { onNext.accept("reply", text); }
        void complete(String text) { onComplete.accept(text); }
        void fail(Throwable error) { onError.accept(error); }
        void usage(ChatResponse response) { onUsage.accept(response); }
    }

    private static final class RecordingChatService implements IOpenAIChatService {
        private final BlockingQueue<Attempt> pending = new LinkedBlockingQueue<>();
        private final List<Attempt> attempts = new CopyOnWriteArrayList<>();
        private volatile Consumer<Attempt> onStart = attempt -> { };

        Attempt takeAttempt() throws Exception {
            Attempt attempt = pending.poll(3, TimeUnit.SECONDS);
            assertThat(attempt).as("a provider stream should start within three seconds").isNotNull();
            return attempt;
        }

        @Override public String generate(ChatModel chatModel, Prompt prompt) {
            throw new UnsupportedOperationException("streaming tests only");
        }

        @Override public Disposable flowGenerate(Prompt prompt, ChatModel model,
                BiConsumer<String, String> onNext, Consumer<Throwable> onError,
                Consumer<String> onComplete) {
            return flowGenerate(prompt, model, onNext, onError, onComplete, response -> { });
        }

        @Override public Disposable flowGenerate(Prompt prompt, ChatModel model,
                BiConsumer<String, String> onNext, Consumer<Throwable> onError,
                Consumer<String> onComplete, Consumer<ChatResponse> onUsage) {
            Attempt attempt = new Attempt(prompt, model, onNext, onError, onComplete, onUsage);
            attempts.add(attempt);
            pending.add(attempt);
            onStart.accept(attempt);
            return attempt.disposable;
        }
    }
}
