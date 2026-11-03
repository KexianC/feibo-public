package com.hzmwmt.security;

import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.service.role.impl.AdminRoleServiceImpl;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRoleServiceProtectionTest {
    private AdminRoleMapper mapper;
    private AdminRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminRoleMapper.class);
        service = new AdminRoleServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void superAdminCannotBeRenamed() {
        TRole existing = role(1L, 101L, "SUPER_ADMIN");
        TRole update = role(1L, 101L, "PLATFORM_OWNER");
        when(mapper.selectById(1L)).thenReturn(existing);

        assertThrows(AppException.class, () -> service.updateById(update));
    }

    @Test
    void superAdminCannotBeDeleted() {
        TRole existing = role(1L, 101L, "SUPER_ADMIN");
        when(mapper.selectByIds(anyCollection())).thenReturn(List.of(existing));

        assertThrows(AppException.class, () -> service.removeById(1L));
    }

    @Test
    void legacySuperAdminAliasCannotBeDeleted() {
        TRole existing = role(1L, 101L, "SUPERADMIN");
        when(mapper.selectByIds(anyCollection())).thenReturn(List.of(existing));

        assertThrows(AppException.class, () -> service.removeById(1L));
    }

    @Test
    void customRoleCannotBePromotedByRename() {
        TRole existing = role(2L, 102L, "MARKET_OPERATOR");
        TRole update = role(2L, 102L, "SUPERADMIN");
        when(mapper.selectById(2L)).thenReturn(existing);

        assertThrows(AppException.class, () -> service.updateById(update));
    }

    @Test
    void protectedAliasCanOnlyBeNormalizedToSameCanonicalCode() {
        TRole existing = role(1L, 101L, "SUPERADMIN");
        TRole update = role(1L, 101L, " super_admin ");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.updateById(any(TRole.class))).thenReturn(1);

        assertTrue(service.updateById(update));
        assertEquals("SUPER_ADMIN", update.getName());
    }

    @Test
    void protectedRoleCannotBeCreatedThroughGenericRoleApi() {
        TRole role = role(null, null, "SUPER_ADMIN");

        assertThrows(AppException.class, () -> service.save(role));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EXPERT", "ORG_ADMIN", "DOCTOR", "DOCTOR_ASSISTANT"})
    void everyBuiltInRoleIsProtectedFromGenericDeletion(String roleName) {
        TRole existing = role(1L, 101L, roleName);
        when(mapper.selectByIds(anyCollection())).thenReturn(List.of(existing));

        assertThrows(AppException.class, () -> service.removeById(1L));
    }

    @Test
    void ordinaryCustomRoleCanStillBeRenamed() {
        TRole existing = role(2L, 102L, "MARKET_OPERATOR");
        TRole update = role(2L, 102L, "MARKET_ANALYST");
        when(mapper.selectById(2L)).thenReturn(existing);
        when(mapper.updateById(any(TRole.class))).thenReturn(1);

        assertTrue(service.updateById(update));
        assertEquals("MARKET_ANALYST", update.getName());
    }

    @Test
    void customRoleIdentifiersAreAlwaysGeneratedByTheServer() {
        TRole role = role(999L, 888L, "MARKET_OPERATOR");
        when(mapper.insert(any(TRole.class))).thenReturn(1);

        assertTrue(service.save(role));
        assertEquals(null, role.getId());
        assertNotEquals(888L, role.getRoleId());
    }

    private TRole role(Long id, Long roleId, String name) {
        TRole role = new TRole();
        role.setId(id);
        role.setRoleId(roleId);
        role.setName(name);
        return role;
    }
}
