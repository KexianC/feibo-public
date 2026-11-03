package com.hzmwmt.modelnotification;

/** Identifiers remain strings across JSON to preserve 64-bit IDs in the admin browser. */
public record ModelFailureNotice(
        String id, String requestId, String userId, String conversationId, String originalModelConfigId,
        String originalProvider, String originalModel, String errorType,
        String fallbackProvider, String fallbackModel, String outcome, String createdAt, boolean read) {
}
