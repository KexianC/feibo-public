package com.hzmwmt.infrastructure.gateway.ai.openai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IOpenAIChatService
 * @Date: 2025/9/23 2:04
 */
public interface IOpenAIChatService {
    default Disposable flowGenerate(Prompt prompt, ChatModel model,
            BiConsumer<String, String> onNext, Consumer<Throwable> onError,
            Consumer<String> onComplete, Consumer<org.springframework.ai.chat.model.ChatResponse> onUsage) {
        return flowGenerate(prompt, model, onNext, onError, onComplete);
    }
    String generate(ChatModel chatModel, Prompt prompt);

    Disposable flowGenerate(Prompt prompt,
                            ChatModel chatModel,
                            BiConsumer<String, String> onNext,
                            Consumer<Throwable> onError,
                            Consumer<String> onComplete
    );
}
