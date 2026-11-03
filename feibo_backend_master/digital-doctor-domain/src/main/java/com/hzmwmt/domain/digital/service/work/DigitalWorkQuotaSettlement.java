package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigitalWorkQuotaSettlement {
    private final IEntitlementService entitlements;
    public DigitalWorkQuotaSettlement(IEntitlementService entitlements) { this.entitlements = entitlements; }

    /** Both callback and authenticated polling pass here before exposing a completed video. */
    @Transactional(rollbackFor = Exception.class)
    public void settle(TDigitalWork work, boolean success, int durationSeconds) {
        if (work.getQuotaBizKey() == null) return; // Tasks created before membership enforcement.
        if (success) {
            entitlements.resize(work.getUserId(), work.getQuotaBizKey(), durationSeconds);
            entitlements.commit(work.getUserId(), work.getQuotaBizKey());
        } else {
            entitlements.release(work.getUserId(), work.getQuotaBizKey());
        }
    }
}
