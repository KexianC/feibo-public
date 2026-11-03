package com.hzmwmt.infrastructure.gateway.ai.tencent;

import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ChatRequestDTO;
import reactor.core.Disposable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITencentAgentChatService
 * @Date: 2025/9/12 10:39
 */
public interface ITencentAgentChatService {
    /**
     * 核心的流式生成方法
     */
    Disposable flowGenerate(ChatRequestDTO request, TencentSseCallback callback);

    interface TencentSseCallback {
        void onReply(String type, String content);

        void onError(Throwable error);

        void onComplete(String finalFullResponse);
    }
}
