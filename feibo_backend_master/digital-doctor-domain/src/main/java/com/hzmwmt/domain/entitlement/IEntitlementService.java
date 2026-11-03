package com.hzmwmt.domain.entitlement;

import java.util.Map;

/** Persistent entitlements; every mutation joins the caller's transaction. */
public interface IEntitlementService {
    String PREMIUM_CHAT = "PREMIUM_CHAT";
    String DIGITAL_HUMAN_SECONDS = "DIGITAL_HUMAN_SECONDS";
    String AI_VIDEO_SECONDS = "AI_VIDEO_SECONDS";
    void reserve(Long userId, String resource, long units, String bizKey);
    void resize(Long userId, String bizKey, long actualUnits);
    void commit(Long userId, String bizKey);
    void release(Long userId, String bizKey);
    boolean isUnlimited(Long userId);
    Map<String, Object> snapshot(Long userId);
    String snapshotProduct(boolean recharge, Long productId);
    void grantPaidOrder(Long userId, Long orderId, String snapshot);
    void grant(Long userId, EntitlementGrantRequest request, Long operatorId);
}
