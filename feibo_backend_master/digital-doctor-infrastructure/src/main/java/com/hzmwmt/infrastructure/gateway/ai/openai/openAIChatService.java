package com.hzmwmt.infrastructure.gateway.ai.openai;

import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName openAIChatService
 * @Date: 2025/9/23 2:09
 */
@Service
@Slf4j
public class openAIChatService implements IOpenAIChatService {

    @Override
    public String generate(ChatModel chatModel, Prompt prompt) {
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    @Override
    public Disposable flowGenerate(Prompt prompt,
                                   ChatModel chatModel,
                                   BiConsumer<String, String> onNext,
                                   Consumer<Throwable> onError,
                                   Consumer<String> onCompleteWithResult) {
        return flowGenerate(prompt, chatModel, onNext, onError, onCompleteWithResult, response -> {});
    }

    @Override
    public Disposable flowGenerate(Prompt prompt, ChatModel chatModel,
            BiConsumer<String, String> onNext, Consumer<Throwable> onError,
            Consumer<String> onCompleteWithResult,
            Consumer<org.springframework.ai.chat.model.ChatResponse> onUsage) {
        StringBuilder responseBuilder = new StringBuilder();
        return chatModel.stream(prompt).subscribe(
                chatResponse -> {
                    onUsage.accept(chatResponse);
                    if (chatResponse.getResult() != null) {
                        String text = chatResponse.getResult().getOutput().getText();
                        if (StringUtil.isNotBlank(text)) {
                            responseBuilder.append(text);
                            onNext.accept("reply", text);
                        }
                    }


                },
                onError,
                () -> {
                    String finalResponse = responseBuilder.toString();
                    log.info("OpenAI 流式响应完成，总响应长度: {}", finalResponse.length());
                    onCompleteWithResult.accept(finalResponse);
                }
        );
    }
}
