package com.hzmwmt.infrastructure.gateway.ai.openai;

import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;

/** Streaming usage is a cumulative snapshot. Never sum repeated usage chunks. */
public final class ChatUsageCapture {
    private ChatUsageCapture() {}
    public static void capture(ChatUsageRecord record, ChatResponse response) {
        if (response == null || response.getMetadata() == null) return;
        if (response.getMetadata().getModel() != null && !response.getMetadata().getModel().isBlank())
            record.setResponseModel(response.getMetadata().getModel());
        var usage = response.getMetadata().getUsage();
        if (usage == null || !(usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage)) return;
        Integer input = nativeUsage.promptTokens();
        Integer output = nativeUsage.completionTokens();
        if (input == null || output == null || input < 0 || output < 0) return;
        // Spring AI emits synthetic empty usage on some intermediate chunks.
        if (input == 0 && output == 0 && (nativeUsage.totalTokens() == null || nativeUsage.totalTokens() == 0)) return;
        Integer cache = nativeUsage.promptCacheHitTokens();
        if (cache == null && nativeUsage.promptTokensDetails() != null) cache = nativeUsage.promptTokensDetails().cachedTokens();
        if (cache != null && (cache < 0 || cache > input)) return;
        long total = (long) input + output;
        if (nativeUsage.totalTokens() != null && nativeUsage.totalTokens().longValue() != total) return;
        record.setInputTokens(input.longValue());
        record.setOutputTokens(output.longValue());
        record.setTotalTokens(total);
        record.setCachedTokens(cache == null ? null : cache.longValue());
        if (nativeUsage.completionTokenDetails() != null && nativeUsage.completionTokenDetails().reasoningTokens() != null)
            record.setReasoningTokens(nativeUsage.completionTokenDetails().reasoningTokens().longValue());
        record.setUsageSource("PROVIDER");
    }
}
