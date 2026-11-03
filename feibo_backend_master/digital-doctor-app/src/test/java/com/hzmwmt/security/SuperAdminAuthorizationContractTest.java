package com.hzmwmt.security;

import com.hzmwmt.domain.controller.log.LoginLogController;
import com.hzmwmt.domain.controller.log.OperationLogsController;
import com.hzmwmt.domain.controller.role.AdminRoleController;
import com.hzmwmt.domain.controller.role.AdminUserRoleMapController;
import com.hzmwmt.domain.controller.user.AdminUserController;
import com.hzmwmt.domain.service.order.impl.AdminUserRoleMapServiceImpl;
import com.hzmwmt.domain.service.role.impl.AdminRoleServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SuperAdminAuthorizationContractTest {

    @Test
    void identityManagementAndAuditBoundariesRequireSuperAdmin() {
        List<Class<?>> superAdminOnlyTypes = List.of(
                AdminRoleController.class,
                AdminUserRoleMapController.class,
                LoginLogController.class,
                OperationLogsController.class,
                AdminRoleServiceImpl.class,
                AdminUserRoleMapServiceImpl.class);

        for (Class<?> type : superAdminOnlyTypes) {
            PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                    type,
                    PreAuthorize.class);
            assertNotNull(annotation, () -> type.getSimpleName() + " must declare a security boundary");
            assertEquals(
                    "hasRole('SUPER_ADMIN')",
                    annotation.value(),
                    () -> type.getSimpleName() + " must reject ordinary administrators");
        }
    }

    @Test
    void userReadsAllowAdministratorsButMutationsRequireSuperAdmin() throws Exception {
        assertMethodRole("page", "hasAuthority('user.read') or hasRole('SUPER_ADMIN')",
                Integer.class, Integer.class, String.class, String.class, Integer.class, Integer.class);
        assertMethodRole("getAvailableModels", "hasAuthority('user.read') or hasRole('SUPER_ADMIN')");
        assertMethodRole("getUserInfo", "hasAuthority('user.read') or hasRole('SUPER_ADMIN')", Long.class);

        assertMethodRole("updateUser", "hasRole('SUPER_ADMIN')", Long.class,
                com.hzmwmt.domain.dto.AdminUserUpdateDTO.class);
        assertMethodRole("setUserStatus", "hasRole('SUPER_ADMIN')", Long.class,
                com.hzmwmt.domain.dto.AdminUserStatusUpdateDTO.class);
        assertMethodRole("createUserByExcel", "hasRole('SUPER_ADMIN')",
                org.springframework.web.multipart.MultipartFile.class);
        assertMethodRole("createUser", "hasRole('SUPER_ADMIN')",
                com.hzmwmt.domain.dto.UserCreateRequest.class);
    }

    private static void assertMethodRole(
            String methodName,
            String expectedExpression,
            Class<?>... parameterTypes) throws Exception {
        Method method = AdminUserController.class.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        assertNotNull(annotation, () -> methodName + " must declare a security boundary");
        assertEquals(expectedExpression, annotation.value());
    }
}
