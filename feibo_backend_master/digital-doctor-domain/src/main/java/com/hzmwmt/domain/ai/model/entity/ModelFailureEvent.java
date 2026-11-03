package com.hzmwmt.domain.ai.model.entity;

/** Final failure/fallback outcome. Never include prompts, credentials or raw provider error bodies. */
public record ModelFailureEvent(
        String requestId,
        Long userId,
        Long conversationId,
        Long originalModelConfigId,
        String originalProvider,
        String originalModel,
        String errorType,
        String fallbackProvider,
        String fallbackModel,
        String outcome) {
}
