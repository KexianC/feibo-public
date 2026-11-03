package com.hzmwmt.security;

import com.hzmwmt.domain.controller.packages.AdminAiMembershipPlanController;
import com.hzmwmt.domain.controller.packages.AdminAiRechargePackController;
import com.hzmwmt.domain.dto.AdminMembershipPlanStatusDTO;
import com.hzmwmt.domain.entity.packages.TAiMembershipPlan;
import com.hzmwmt.domain.entity.packages.TAiRechargePack;
import com.hzmwmt.domain.service.packages.AdminAiMembershipPlanService;
import com.hzmwmt.domain.service.packages.AdminAiRechargePackService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialPayloadSecurityTest {

    @Test
    void membershipCreateIgnoresClientControlledIdentifiersAndTimestamps() {
        AdminAiMembershipPlanService service = mock(AdminAiMembershipPlanService.class);
        AdminAiMembershipPlanController controller = new AdminAiMembershipPlanController();
        ReflectionTestUtils.setField(controller, "adminAiMembershipPlanService", service);
        when(service.save(any())).thenReturn(true);

        TAiMembershipPlan request = membership(777);
        request.setAiMembershipPlanId(888L);
        request.setPlanCode("ATTACKER_CODE");
        request.setCreateTime(new Date(1));
        request.setUpdateTime(new Date(1));

        controller.save(request);

        var captor = org.mockito.ArgumentCaptor.forClass(TAiMembershipPlan.class);
        verify(service).save(captor.capture());
        TAiMembershipPlan saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getAiMembershipPlanId()).isNotEqualTo(888L);
        assertThat(saved.getPlanCode()).startsWith("PLAN_").doesNotContain("ATTACKER");
        assertThat(saved.getCreateTime()).isNull();
        assertThat(saved.getUpdateTime()).isNull();
    }

    @Test
    void membershipUpdateOnlyPassesMutableFieldsAndStatusBodyIsExplicit() {
        AdminAiMembershipPlanService service = mock(AdminAiMembershipPlanService.class);
        AdminAiMembershipPlanController controller = new AdminAiMembershipPlanController();
        ReflectionTestUtils.setField(controller, "adminAiMembershipPlanService", service);
        when(service.updateById(any())).thenReturn(true);

        TAiMembershipPlan request = membership(9);
        request.setAiMembershipPlanId(888L);
        request.setPlanCode("ATTACKER_CODE");
        request.setCreateTime(new Date(1));
        controller.update(request);

        AdminMembershipPlanStatusDTO status = new AdminMembershipPlanStatusDTO();
        status.setIsActive(false);
        controller.updateStatus(9, status);

        var captor = org.mockito.ArgumentCaptor.forClass(TAiMembershipPlan.class);
        verify(service, org.mockito.Mockito.times(2)).updateById(captor.capture());
        TAiMembershipPlan update = captor.getAllValues().get(0);
        assertThat(update.getId()).isEqualTo(9);
        assertThat(update.getAiMembershipPlanId()).isNull();
        assertThat(update.getPlanCode()).isNull();
        assertThat(update.getCreateTime()).isNull();
        assertThat(captor.getAllValues().get(1).getIsActive()).isFalse();
    }

    @Test
    void rechargeCreateAndUpdateCannotOverwriteServerFields() {
        AdminAiRechargePackService service = mock(AdminAiRechargePackService.class);
        AdminAiRechargePackController controller = new AdminAiRechargePackController();
        ReflectionTestUtils.setField(controller, "adminAiRechargePackService", service);
        when(service.save(any())).thenReturn(true);
        when(service.updateById(any())).thenReturn(true);

        TAiRechargePack request = recharge(7);
        request.setAiRechargePackId(888L);
        request.setPackCode("ATTACKER_CODE");
        request.setCreateTime(new Date(1));
        controller.save(request);
        assertThat(request.getId()).isNull();
        assertThat(request.getAiRechargePackId()).isNull();
        assertThat(request.getPackCode()).isNull();
        assertThat(request.getCreateTime()).isNull();

        TAiRechargePack updateRequest = recharge(7);
        updateRequest.setAiRechargePackId(999L);
        updateRequest.setPackCode("ATTACKER_CODE");
        controller.update(updateRequest);

        var captor = org.mockito.ArgumentCaptor.forClass(TAiRechargePack.class);
        verify(service).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7);
        assertThat(captor.getValue().getAiRechargePackId()).isNull();
        assertThat(captor.getValue().getPackCode()).isNull();
    }

    private static TAiMembershipPlan membership(int id) {
        TAiMembershipPlan plan = new TAiMembershipPlan();
        plan.setId(id);
        plan.setName("年度套餐");
        plan.setPrice(new BigDecimal("99.00"));
        plan.setDurationDays(365);
        plan.setIsActive(true);
        return plan;
    }

    private static TAiRechargePack recharge(int id) {
        TAiRechargePack pack = new TAiRechargePack();
        pack.setId(id);
        pack.setName("算力包");
        pack.setPrice(new BigDecimal("9.90"));
        pack.setValue(new BigDecimal("100"));
        pack.setIsActive((byte) 0);
        return pack;
    }
}
