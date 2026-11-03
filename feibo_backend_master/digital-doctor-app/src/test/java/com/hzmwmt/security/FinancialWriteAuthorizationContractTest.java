package com.hzmwmt.security;

import com.hzmwmt.domain.controller.order.AdminOrderController;
import com.hzmwmt.domain.controller.packages.AdminAiMembershipPlanController;
import com.hzmwmt.domain.controller.packages.AdminAiRechargePackController;
import com.hzmwmt.domain.entity.order.TAdminOrder;
import com.hzmwmt.domain.mapper.order.AdminOrderMapper;
import com.hzmwmt.domain.service.order.impl.AdminOrderServiceImpl;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialWriteAuthorizationContractTest {

    @Test
    void allFinancialMutationEndpointsRequireSuperAdministrator() throws Exception {
        assertSuper(AdminOrderController.class.getMethod("updateStatus", Long.class, Integer.class));
        assertSuper(AdminAiMembershipPlanController.class.getMethod("save",
                com.hzmwmt.domain.entity.packages.TAiMembershipPlan.class));
        assertSuper(AdminAiMembershipPlanController.class.getMethod("update",
                com.hzmwmt.domain.entity.packages.TAiMembershipPlan.class));
        assertSuper(AdminAiMembershipPlanController.class.getMethod("updateStatus", Integer.class,
                com.hzmwmt.domain.dto.AdminMembershipPlanStatusDTO.class));
        assertSuper(AdminAiRechargePackController.class.getMethod("save",
                com.hzmwmt.domain.entity.packages.TAiRechargePack.class));
        assertSuper(AdminAiRechargePackController.class.getMethod("update",
                com.hzmwmt.domain.entity.packages.TAiRechargePack.class));
        assertSuper(AdminAiRechargePackController.class.getMethod("remove", Integer.class));
    }

    @Test
    void manualOrderUpdateCannotForgePaymentSuccess() {
        AdminOrderMapper mapper = mock(AdminOrderMapper.class);
        AdminOrderServiceImpl service = new AdminOrderServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        TAdminOrder order = new TAdminOrder();
        order.setOrderId(88L);
        order.setStatus(1);
        when(mapper.selectByOrderId(88L)).thenReturn(order);

        assertThrows(AppException.class, () -> service.updateOrderStatus(88L, 2));

        verify(mapper, never()).update(any(), any());
    }

    @Test
    void legitimatePaidToCompletedTransitionUsesCompareAndSetUpdate() {
        AdminOrderMapper mapper = mock(AdminOrderMapper.class);
        AdminOrderServiceImpl service = new AdminOrderServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        TAdminOrder order = new TAdminOrder();
        order.setOrderId(88L);
        order.setStatus(2);
        when(mapper.selectByOrderId(88L)).thenReturn(order);
        when(mapper.update(any(), any())).thenReturn(1);

        assertTrue(service.updateOrderStatus(88L, 3));

        verify(mapper).update(any(), any());
    }

    private void assertSuper(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}
