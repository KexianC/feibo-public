package com.hzmwmt.security;

import com.hzmwmt.domain.controller.role.AdminPermissionController;
import com.hzmwmt.domain.dto.RolePermissionUpdateDTO;
import com.hzmwmt.domain.service.role.impl.AdminPermissionServiceImpl;
import com.hzmwmt.types.annotations.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRbacAuthorizationContractTest {

    @Test
    void onlySuperAdminCanReachPermissionManagementController() {
        PreAuthorize boundary = AnnotatedElementUtils.findMergedAnnotation(
                AdminPermissionController.class,
                PreAuthorize.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }

    @Test
    void replacementHasDefenseInDepthTransactionAndCriticalAudit() throws Exception {
        Method method = AdminPermissionServiceImpl.class.getMethod(
                "replaceRolePermissions",
                Long.class,
                RolePermissionUpdateDTO.class);

        PreAuthorize boundary = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        OperationLog audit = AnnotatedElementUtils.findMergedAnnotation(method, OperationLog.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).isEqualTo("hasRole('SUPER_ADMIN')");
        assertThat(transaction).isNotNull();
        assertThat(transaction.rollbackFor()).contains(Exception.class);
        assertThat(audit).isNotNull();
        assertThat(audit.critical()).isTrue();
        assertThat(audit.recordParams()).isTrue();
    }

    @Test
    void reservedCatalogHasNoHttpDeletionSurface() {
        assertThat(Arrays.stream(AdminPermissionController.class.getDeclaredMethods())
                .anyMatch(method -> AnnotatedElementUtils.findMergedAnnotation(
                        method,
                        org.springframework.web.bind.annotation.DeleteMapping.class) != null))
                .isFalse();
    }
}
