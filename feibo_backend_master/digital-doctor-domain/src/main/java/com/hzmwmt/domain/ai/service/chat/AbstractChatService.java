package com.hzmwmt.domain.ai.service.chat;

import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ChatProcessAggregate;
import com.hzmwmt.domain.ai.model.aggregate.CreateChatHistoryAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.IChatService;
import com.hzmwmt.domain.ai.service.chat.model.factory.DefaultModelStrategyFactory;
import com.hzmwmt.domain.ai.service.chat.model.strategy.AbstractModelStrategyService;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AbstractChatService
 * @Date: 2025/9/12 11:37
 */
@Slf4j
public abstract class AbstractChatService implements IChatService {
    private static final Set<String> SAFE_REMOTE_ERROR_MESSAGES = Set.of(
            "消息命中敏感内容，发送失败",
            "超出并发数限制"
    );

    @Resource
    protected AIChatValidationFactory aiChatValidationFactory;
    @Resource
    protected IAIRepository repository;
    @Resource
    protected IAIPort aiPort;
    @Resource
    protected DefaultModelStrategyFactory defaultModelStrategyFactory;
    @Resource
    protected IEntitlementService entitlementService;

    @Override
    public List<String> getAiRecommendSearch() {
        return aiPort.getAiRecommendSearch();
    }

    @Override
    public SseEmitter completions(ChatProcessAggregate chatProcessAggregate) {
        AIMessageEntity aiMessageEntity = chatProcessAggregate.getAiMessageEntity();
        Boolean isNewConversation = aiMessageEntity.getAiConversationId() == null;
        //参数装填
        AIChatRuleCommandAggregate aggregate = AIChatRuleCommandAggregate.builder()
                .userId(chatProcessAggregate.getUserId())
                .aiModelConfigLeafEntity(
                        AIModelConfigEntity.builder()
                                //默认普通模型
                                .type(false)
                                .build()
                )
                .aiMessageEntity(aiMessageEntity)
                .resourceUserTypeEnumVO(chatProcessAggregate.getResourceUserTypeEnumVO())
                .build();
        //责任链过滤
        RuleLogicEntity<AIChatRuleCommandAggregate> ruleLogic = doCheckLogic(aggregate,
                AIChatValidationFactory.LogicModel.PARAMETER_VALIDATION.getCode(),
                AIChatValidationFactory.LogicModel.CONTENT_VALIDATION.getCode()
        );

        //构建SSE
        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);
        //判断过滤结果
        if (!LogicCheckTypeVO.SUCCESS.equals(ruleLogic.getType())) {
            try {
                emitter.send(SseEmitter.event().name("error_message").data(ruleLogic.getInfo()));
            } catch (IOException ex) {
                log.info("问答请求错误消息发送失败，异常类型: {}", ex.getClass().getSimpleName());
            } finally {
                emitter.complete();
            }
            return emitter;
        }
        // 责任链过滤成功后，获取有效数据
        AIChatRuleCommandAggregate data = ruleLogic.getData();
        AIModelConfigEntity aiModelConfigLeafEntity = data.getAiModelConfigLeafEntity();
        AIMessageEntity currentUserMessage = data.getAiMessageEntity(); // 过滤后的用户消息
        String modelName = aiModelConfigLeafEntity.getName();
        String userContent = currentUserMessage.getContent(); // 用户消息内容
        // Classification is loaded from the server model record by ParameterValidationFilter.
        // Ordinary models never consume a membership allowance or the legacy balance.
        final boolean premium = Boolean.TRUE.equals(aiModelConfigLeafEntity.getType());
        final String quotaKey = "chat:" + UUID.randomUUID();
        final AtomicBoolean quotaSettled = new AtomicBoolean(false);
        if (premium) {
            try {
                entitlementService.reserve(chatProcessAggregate.getUserId(), "PREMIUM_CHAT", 1, quotaKey);
            } catch (AppException exception) {
                try {
                    emitter.send(SseEmitter.event().name("error_message").data(exception.getInfo()));
                } catch (IOException ignored) {
                    // A rejected request has not reached the provider.
                } finally {
                    emitter.complete();
                }
                return emitter;
            }
        }


        // ###########################
        // 新增：计算用户消息Token（在调用AI接口前完成）
        // ###########################
        try {
            // 1. 获取模型策略服务（如百川/Deepseek）
            AbstractModelStrategyService modelStrategy = defaultModelStrategyFactory.getAbstractStrategyService(aiModelConfigLeafEntity.getModelCode());

            // 2. 计算用户当前消息的Token（调用策略服务的方法，无需历史）
            int userTokenCount = modelStrategy.calculateTotalTokens(currentUserMessage);
            Long userToken = (long) userTokenCount;

            // 3. 存储到消息实体中（供后续持久化使用）
            currentUserMessage.setToken(userToken);
            log.info("用户消息Token计算完成：模型[{}]，内容长度[{}]，Token数[{}]",
                    modelName, userContent == null ? 0 : userContent.length(), userToken);
        } catch (Exception e) {
            log.error("计算用户消息Token失败，默认按0处理，异常类型: {}",
                    e.getClass().getSimpleName());
            currentUserMessage.setToken(0L); // 异常兜底
        }


        //关闭请求线程。使用原子变量来控制并发
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        //控制complete的并发
        final AtomicBoolean isCompleted = new AtomicBoolean(false);

        //清理回调
        final Runnable cleanup = () -> {
            synchronized (isCompleted) {
            // 使用原子操作，保证只有一个线程能进入这个代码块
            if (isCompleted.compareAndSet(false, true)) {
                if (premium && quotaSettled.compareAndSet(false, true)) {
                    try {
                        entitlementService.release(chatProcessAggregate.getUserId(), quotaKey);
                    } catch (RuntimeException exception) {
                        log.error("AI allowance refund requires reconciliation; userId={}, bizKey={}",
                                chatProcessAggregate.getUserId(), quotaKey);
                    }
                }
                log.info("开始执行清理任务，模型: {}", modelName);
                Disposable subscription = disposableRef.get();
                if (subscription != null && !subscription.isDisposed()) {
                    //取消后端的AI任务，避免资源浪费
                    subscription.dispose();
                }
                // 关闭前端的SSE连接
                emitter.complete();
            }
            }
        };

        // 安全发送消息的辅助方法
        final BiConsumer<String, Object> safeSend = (eventName, message) -> {
            synchronized (isCompleted) {
            // 原子性检查，不修改值
            if (isCompleted.compareAndSet(false, false)) {
                try {
                    // Commit before exposing answer text, serialized with disconnect cleanup.
                    if (premium && message instanceof String text && !text.isBlank()
                            && (eventName == null || (!"error_message".equals(eventName)
                            && !"aiConversationId".equals(eventName)
                            && !"model_fallback".equals(eventName))) && !quotaSettled.get()) {
                        entitlementService.commit(chatProcessAggregate.getUserId(), quotaKey);
                        quotaSettled.set(true);
                    }
                    if (eventName != null) {
                        emitter.send(SseEmitter.event().name(eventName).data(message));
                    } else {
                        emitter.send(message);
                    }
                } catch (IOException | RuntimeException e) {
                    log.error("发送 SSE 消息失败，异常类型: {}", e.getClass().getSimpleName());
                    // 直接触发清理，不发送错误消息
                    cleanup.run();
                }
            }
            }
        };
        //断开回调
        emitter.onCompletion(() -> {
            log.info("流式问答请求完成，使用模型：{}", modelName);
            cleanup.run();
        });
        //超时回调
        emitter.onTimeout(() -> {
            log.info("流式问答请求超时，使用模型：{}", modelName);
            safeSend.accept("error_message", ResponseCode.INTERNET_EXCEPTION.getInfo());
            cleanup.run();
        });
        final Consumer<Throwable> errorHandler = error -> {
            log.error("流式问答请求异常，使用模型：{}，异常类型: {}",
                    modelName, error == null ? "unknown" : error.getClass().getSimpleName());
            safeSend.accept("error_message", clientSafeStreamError(error));
            cleanup.run();
        };

        //使用策略服务
        try {
        Disposable disposable = defaultModelStrategyFactory.getStrategyService(aiModelConfigLeafEntity.getModelCode()
        ).doMessageResponse(
                ModelProcessAggregate.builder()
                        .aiMessageEntity(aiMessageEntity)
                        .isAllHistory(chatProcessAggregate.getIsAllHistory())
                        .aiModelConfigEntity(aiModelConfigLeafEntity)
                        .userId(chatProcessAggregate.getUserId())
                        //构建回调
                        .onNext(safeSend::accept)
                        .onCompleteWithResult(
                                result -> {
                                    try {
                                        synchronized (isCompleted) {
                                            if (isCompleted.get()) return;
                                            if (premium && result != null && !result.isBlank() && !quotaSettled.get()) {
                                                entitlementService.commit(chatProcessAggregate.getUserId(), quotaKey);
                                                quotaSettled.set(true);
                                            }
                                        }
                                        // ###########################
                                        // 4. 计算【AI回答Token】（完整回答已拼接）
                                        // ###########################
                                        try {
                                            AbstractModelStrategyService modelStrategy = defaultModelStrategyFactory
                                                    .getAbstractStrategyService(aiModelConfigLeafEntity.getModelCode());
                                            // 构建AI消息实体（用于计算Token）
                                            AIMessageEntity aiMessage = AIMessageEntity.builder()
                                                    .content(result) // 完整AI回答
                                                    .aiChatRoleEnumVO(AIChatRoleEnumVO.ASSISTANT)
                                                    .build();
                                            // 计算AI Token（复用同一策略服务，规则一致）
                                            int aiTokenCount = modelStrategy.calculateTotalTokens(aiMessage);
                                            Long aiToken = (long) aiTokenCount;
                                            currentUserMessage.setAiToken(aiToken);
                                        } catch (Exception e) {
                                            log.error("计算AI回答Token失败，默认按0处理，异常类型: {}",
                                                    e.getClass().getSimpleName());
                                            currentUserMessage.setAiToken(0L);
                                        }

                                        // 消息持久化
                                        Long aiConversationId = repository.saveChatHistory(
                                                CreateChatHistoryAggregate.builder()
                                                        .userId(chatProcessAggregate.getUserId())
                                                        .aiUserMessageEntity(aiMessageEntity)
                                                        .isNewConversation(isNewConversation)
                                                        .result(result)
                                                        .build()
                                        );
                                        safeSend.accept("aiConversationId", aiConversationId);
                                        // 持久化成功后，明确关闭连接
                                        log.info("AI 对话持久化成功，SSE 连接正常关闭。");
                                        cleanup.run();
                                    } catch (Exception e) {
                                        errorHandler.accept(e);
                                    }
                                }
                        )
                        .onError(errorHandler)
                        .build()
        );
        disposableRef.set(disposable);
        if (isCompleted.get() && disposable != null && !disposable.isDisposed()) disposable.dispose();
        } catch (RuntimeException exception) {
            errorHandler.accept(exception);
        }
        return emitter;
    }

    static String clientSafeStreamError(Throwable error) {
        if (error instanceof AppException appException) {
            if (ResponseCode.INTERNET_EXCEPTION.getCode().equals(appException.getCode())) {
                return ResponseCode.INTERNET_EXCEPTION.getInfo();
            }
            if (ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode().equals(appException.getCode())) {
                return SAFE_REMOTE_ERROR_MESSAGES.contains(appException.getInfo())
                        ? appException.getInfo()
                        : ResponseCode.REMOTE_REQUEST_EXCEPTION.getInfo();
            }
        }
        return ResponseCode.SERVICE_ERROR.getInfo();
    }


    protected abstract RuleLogicEntity<AIChatRuleCommandAggregate> doCheckLogic(AIChatRuleCommandAggregate aiChatRuleCommandAggregate, String... logics);
}
