package com.hzmwmt.security;

import com.hzmwmt.domain.controller.dashboard.DashboardController;
import com.hzmwmt.domain.controller.function.AdminBackgroundImageController;
import com.hzmwmt.domain.controller.function.AdminCaseVideoController;
import com.hzmwmt.domain.controller.function.AdminCopywritingApprovalController;
import com.hzmwmt.domain.controller.function.AdminCopywritingCategoryController;
import com.hzmwmt.domain.controller.function.AdminCopywritingController;
import com.hzmwmt.domain.controller.function.AdminCopywritingExpertAssignmentController;
import com.hzmwmt.domain.controller.function.AdminCopywritingOrganizationAdminAssignmentController;
import com.hzmwmt.domain.controller.function.AdminDigitalModelController;
import com.hzmwmt.domain.controller.function.AdminDigitalMusicController;
import com.hzmwmt.domain.controller.function.AdminDigitalTemplateController;
import com.hzmwmt.domain.controller.function.AdminIllustrationController;
import com.hzmwmt.domain.controller.function.AdminVideoController;
import com.hzmwmt.domain.controller.function.AdminVoiceController;
import com.hzmwmt.domain.controller.function.DigitalModelController;
import com.hzmwmt.domain.controller.log.LoginLogController;
import com.hzmwmt.domain.controller.log.OperationLogsController;
import com.hzmwmt.domain.controller.order.AdminOrderController;
import com.hzmwmt.domain.controller.packages.AdminAiMembershipPlanController;
import com.hzmwmt.domain.controller.packages.AdminAiRechargePackController;
import com.hzmwmt.domain.controller.role.AdminPermissionController;
import com.hzmwmt.domain.controller.role.AdminRoleController;
import com.hzmwmt.domain.controller.role.AdminUserRoleMapController;
import com.hzmwmt.domain.controller.user.AdminConversationController;
import com.hzmwmt.domain.controller.user.AdminDigitalWorkController;
import com.hzmwmt.domain.controller.user.AdminFeedbackController;
import com.hzmwmt.domain.controller.user.AdminUserController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCapabilityCoverageContractTest {

    private static final String ADMIN_CONSOLE =
            "hasAuthority('admin.console') or hasRole('SUPER_ADMIN')";
    private static final String CONTENT_MANAGE =
            "hasAuthority('content.manage') or hasRole('SUPER_ADMIN')";
    private static final String USER_READ =
            "hasAuthority('user.read') or hasRole('SUPER_ADMIN')";
    private static final String FINANCE_MANAGE =
            "hasAuthority('finance.manage') or hasRole('SUPER_ADMIN')";
    private static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    private static final List<Class<?>> ALL_ADMIN_CONTROLLERS = List.of(
            DashboardController.class,
            AdminBackgroundImageController.class,
            AdminCaseVideoController.class,
            AdminCopywritingApprovalController.class,
            AdminCopywritingCategoryController.class,
            AdminCopywritingController.class,
            AdminCopywritingExpertAssignmentController.class,
            AdminCopywritingOrganizationAdminAssignmentController.class,
            AdminDigitalModelController.class,
            AdminDigitalMusicController.class,
            AdminDigitalTemplateController.class,
            AdminIllustrationController.class,
            AdminVideoController.class,
            AdminVoiceController.class,
            DigitalModelController.class,
            LoginLogController.class,
            OperationLogsController.class,
            AdminOrderController.class,
            AdminAiMembershipPlanController.class,
            AdminAiRechargePackController.class,
            AdminPermissionController.class,
            AdminRoleController.class,
            AdminUserRoleMapController.class,
            AdminConversationController.class,
            AdminDigitalWorkController.class,
            AdminFeedbackController.class,
            AdminUserController.class);

    @Test
    void everyAdminRequestHasAnExplicitMethodOrClassSecurityBoundary() {
        for (Class<?> controller : ALL_ADMIN_CONTROLLERS) {
            PreAuthorize classBoundary = preAuthorize(controller);
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                assertThat(classBoundary != null || preAuthorize(method) != null)
                        .as("%s#%s must fail closed", controller.getSimpleName(), method.getName())
                        .isTrue();
            }
        }
    }

    @Test
    void delegableControllerGroupsUseTheDocumentedCapabilityPolicy() {
        assertClassBoundary(ADMIN_CONSOLE, DashboardController.class);
        assertClassBoundary(CONTENT_MANAGE,
                AdminBackgroundImageController.class,
                AdminCaseVideoController.class,
                AdminCopywritingApprovalController.class,
                AdminCopywritingCategoryController.class,
                AdminCopywritingController.class,
                AdminCopywritingExpertAssignmentController.class,
                AdminCopywritingOrganizationAdminAssignmentController.class,
                AdminDigitalModelController.class,
                AdminDigitalMusicController.class,
                AdminDigitalTemplateController.class,
                AdminIllustrationController.class,
                AdminVideoController.class,
                AdminVoiceController.class,
                DigitalModelController.class);
        assertClassBoundary(USER_READ,
                AdminConversationController.class,
                AdminDigitalWorkController.class,
                AdminFeedbackController.class);
        assertClassBoundary(FINANCE_MANAGE,
                AdminOrderController.class,
                AdminAiMembershipPlanController.class,
                AdminAiRechargePackController.class);
    }

    @Test
    void identityAuditAndMutationsRetainSuperAdminHardGates() throws Exception {
        assertClassBoundary(SUPER_ADMIN,
                AdminPermissionController.class,
                AdminRoleController.class,
                AdminUserRoleMapController.class,
                LoginLogController.class,
                OperationLogsController.class);

        assertMethodBoundary(AdminConversationController.class, "deleteConversation",
                new Class<?>[]{String.class}, SUPER_ADMIN);
        assertMethodBoundary(AdminDigitalWorkController.class, "deleteById",
                new Class<?>[]{Integer.class}, SUPER_ADMIN);
        assertMethodBoundary(AdminDigitalWorkController.class, "deleteBatch",
                new Class<?>[]{List.class}, SUPER_ADMIN);

        assertMethodBoundary(AdminUserController.class, "updateUser",
                new Class<?>[]{Long.class, com.hzmwmt.domain.dto.AdminUserUpdateDTO.class}, SUPER_ADMIN);
        assertMethodBoundary(AdminUserController.class, "setUserStatus",
                new Class<?>[]{Long.class, com.hzmwmt.domain.dto.AdminUserStatusUpdateDTO.class}, SUPER_ADMIN);
        assertMethodBoundary(AdminUserController.class, "createUserByExcel",
                new Class<?>[]{org.springframework.web.multipart.MultipartFile.class}, SUPER_ADMIN);
        assertMethodBoundary(AdminUserController.class, "createUser",
                new Class<?>[]{com.hzmwmt.domain.dto.UserCreateRequest.class}, SUPER_ADMIN);
    }

    private void assertClassBoundary(String expected, Class<?>... controllers) {
        for (Class<?> controller : controllers) {
            PreAuthorize boundary = preAuthorize(controller);
            assertThat(boundary)
                    .as("%s class boundary", controller.getSimpleName())
                    .isNotNull();
            assertThat(boundary.value())
                    .as("%s class boundary", controller.getSimpleName())
                    .isEqualTo(expected);
        }
    }

    private void assertMethodBoundary(
            Class<?> controller,
            String methodName,
            Class<?>[] parameterTypes,
            String expected) throws Exception {
        Method method = controller.getMethod(methodName, parameterTypes);
        PreAuthorize boundary = preAuthorize(method);
        assertThat(boundary)
                .as("%s#%s method boundary", controller.getSimpleName(), methodName)
                .isNotNull();
        assertThat(boundary.value()).isEqualTo(expected);
    }

    private PreAuthorize preAuthorize(java.lang.reflect.AnnotatedElement element) {
        return AnnotatedElementUtils.findMergedAnnotation(element, PreAuthorize.class);
    }
}
