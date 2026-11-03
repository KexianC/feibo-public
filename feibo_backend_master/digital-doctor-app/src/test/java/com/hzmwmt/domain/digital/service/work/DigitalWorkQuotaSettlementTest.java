package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DigitalWorkQuotaSettlementTest {
    @Test void actualOverageMustBeReservedBeforeCompletionCanBeDelivered() {
        var quotas = mock(IEntitlementService.class);
        var service = new DigitalWorkQuotaSettlement(quotas);
        var work = work();
        doThrow(new AppException("QUOTA_EXCEEDED", "额度不足")).when(quotas).resize(42L, "digital:test", 301);
        assertThatThrownBy(() -> service.settle(work, true, 301)).isInstanceOf(AppException.class);
        verify(quotas, never()).commit(anyLong(), anyString());
        verify(quotas, never()).release(anyLong(), anyString());
    }
    @Test void successSettlesActualSecondsAndFailureReleasesTheOriginalOwnerHold() {
        var quotas = mock(IEntitlementService.class);
        var service = new DigitalWorkQuotaSettlement(quotas);
        service.settle(work(), true, 57);
        var ordered = inOrder(quotas);
        ordered.verify(quotas).resize(42L, "digital:test", 57);
        ordered.verify(quotas).commit(42L, "digital:test");
        service.settle(work(), false, 0);
        verify(quotas).release(42L, "digital:test");
    }
    @Test void legacyTasksAreNeverChargedRetroactively() {
        var quotas = mock(IEntitlementService.class);
        var work = work(); work.setQuotaBizKey(null);
        new DigitalWorkQuotaSettlement(quotas).settle(work, true, 123);
        verifyNoInteractions(quotas);
    }
    private TDigitalWork work() {
        var work = new TDigitalWork(); work.setUserId(42L); work.setQuotaBizKey("digital:test"); return work;
    }
}
