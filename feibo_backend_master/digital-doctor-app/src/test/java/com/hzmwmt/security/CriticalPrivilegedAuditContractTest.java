package com.hzmwmt.security;

import com.hzmwmt.domain.dto.AdminUserUpdateDTO;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.service.order.impl.AdminUserRoleMapServiceImpl;
import com.hzmwmt.domain.service.role.impl.AdminRoleServiceImpl;
import com.hzmwmt.domain.service.user.impl.AdminUserServiceImpl;
import com.hzmwmt.types.annotations.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalPrivilegedAuditContractTest {

    @Test
    void roleUserRoleAndAdminUserMutationsAreTransactionallyAudited() throws Exception {
        assertCritical(AdminRoleServiceImpl.class, "save", TRole.class);
        assertCritical(AdminRoleServiceImpl.class, "updateById", TRole.class);
        assertCritical(AdminRoleServiceImpl.class, "removeById", Serializable.class);
        assertCritical(AdminRoleServiceImpl.class, "removeBatchByIds", Collection.class);

        assertCritical(AdminUserRoleMapServiceImpl.class, "save", TUserRoleMap.class);
        assertCritical(AdminUserRoleMapServiceImpl.class, "updateById", TUserRoleMap.class);
        assertCritical(AdminUserRoleMapServiceImpl.class, "removeById", Serializable.class);
        assertCritical(AdminUserRoleMapServiceImpl.class, "removeBatchByIds", Collection.class);

        assertCritical(AdminUserServiceImpl.class, "createUserByExcel", MultipartFile.class);
        assertCritical(AdminUserServiceImpl.class, "createUser", TUser.class, Long.class);
        assertCritical(AdminUserServiceImpl.class, "updateUser", Long.class, AdminUserUpdateDTO.class);
        assertCritical(AdminUserServiceImpl.class, "setUserDisabled", Long.class, boolean.class);
    }

    private static void assertCritical(
            Class<?> type,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        Method method = type.getMethod(methodName, parameterTypes);
        OperationLog audit = AnnotatedElementUtils.findMergedAnnotation(method, OperationLog.class);
        Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(audit)
                .as(type.getSimpleName() + "." + methodName + " audit")
                .isNotNull();
        assertThat(audit.critical()).isTrue();
        assertThat(transaction)
                .as(type.getSimpleName() + "." + methodName + " transaction")
                .isNotNull();
    }
}
