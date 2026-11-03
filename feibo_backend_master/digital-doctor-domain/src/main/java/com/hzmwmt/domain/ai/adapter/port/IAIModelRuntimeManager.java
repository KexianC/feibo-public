package com.hzmwmt.domain.ai.adapter.port;

/**
 * Domain-facing control surface for the runtime chat-model resolver.
 * It deliberately exposes no credential material or Spring AI types.
 */
public interface IAIModelRuntimeManager {

    boolean isSupportedProvider(String modelCode);

    boolean isProviderReady(String modelCode);

    boolean hasFallbackApiKey(String modelCode);

    void invalidate(Long aiModelConfigId);
}
