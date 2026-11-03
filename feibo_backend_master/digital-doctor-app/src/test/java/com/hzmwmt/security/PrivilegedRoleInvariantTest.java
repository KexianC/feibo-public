package com.hzmwmt.security;

import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.mapper.role.AdminUserRoleMapMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.order.AdminUserRoleMapService;
import com.hzmwmt.domain.service.order.impl.AdminUserRoleMapServiceImpl;
import com.hzmwmt.domain.service.user.impl.AdminUserServiceImpl;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivilegedRoleInvariantTest {

    @Test
    void cannotRemoveLastActiveSuperAdministrator() {
        RoleMapFixture fixture = roleMapFixture(900L, 100L);
        when(fixture.roleMapMapper.selectActiveSuperAdminUserIdsForUpdate())
                .thenReturn(List.of(100L));

        assertThrows(AppException.class, () -> fixture.service.removeById(1));
        verify(fixture.roleMapMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void canRemoveOneSuperAdministratorWhenAnotherActiveOneRemains() {
        RoleMapFixture fixture = roleMapFixture(900L, 100L);
        when(fixture.roleMapMapper.selectActiveSuperAdminUserIdsForUpdate())
                .thenReturn(List.of(100L, 200L));
        when(fixture.roleMapMapper.deleteById(any(Serializable.class))).thenReturn(1);

        assertTrue(fixture.service.removeById(1));
    }

    @Test
    void cannotModifyOwnPrivilegedRole() {
        RoleMapFixture fixture = roleMapFixture(100L, 100L);

        assertThrows(AppException.class, () -> fixture.service.removeById(1));
        verify(fixture.roleMapMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void cannotDisableCurrentAccount() {
        AdminUserMapper userMapper = mock(AdminUserMapper.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper);
        AuthService authService = mock(AuthService.class);
        AdminUserRoleMapService roleMapService = mock(AdminUserRoleMapService.class);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        ReflectionTestUtils.setField(service, "authService", authService);
        ReflectionTestUtils.setField(service, "adminUserRoleMapService", roleMapService);

        TUser user = new TUser();
        user.setId(1L);
        user.setUserId(100L);
        user.setStatus(false);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(authService.queryValidatedUser())
                .thenReturn(AuthUserEntity.builder().userId(100L).build());

        assertThrows(AppException.class, () -> service.setUserDisabled(1L, true));
        verify(roleMapService, never()).assertCanDisableUser(any());
    }

    @Test
    void explicitStatusUpdateIsIdempotentAndNeverToggles() {
        AdminUserMapper userMapper = mock(AdminUserMapper.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);

        TUser alreadyDisabled = new TUser();
        alreadyDisabled.setId(1L);
        alreadyDisabled.setUserId(100L);
        alreadyDisabled.setStatus(true);
        when(userMapper.selectById(1L)).thenReturn(alreadyDisabled);

        assertTrue(service.setUserDisabled(1L, true));

        verify(userMapper, never()).updateById(any(TUser.class));
        assertTrue(alreadyDisabled.getStatus());
    }

    @Test
    void administratorCreatedAccountNeverUsesPredictablePhoneSuffixPassword() {
        AdminUserMapper userMapper = mock(AdminUserMapper.class);
        AdminUserRoleMapService roleMapService = mock(AdminUserRoleMapService.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(userMapper);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        ReflectionTestUtils.setField(service, "adminUserRoleMapService", roleMapService);
        when(userMapper.insert(any(TUser.class))).thenReturn(1);
        when(roleMapService.save(any(TUserRoleMap.class))).thenReturn(true);

        TUser user = new TUser();
        user.setPhoneNumber("13175144211");
        user.setNickName("new-user");

        TUser created = service.createUser(user, null);

        assertNotNull(created.getUserId());
        assertNotNull(created.getPassword());
        assertFalse(new BCryptPasswordEncoder().matches("144211", created.getPassword()));
        verify(roleMapService).save(any(TUserRoleMap.class));
    }

    private RoleMapFixture roleMapFixture(Long actorUserId, Long targetUserId) {
        AdminUserRoleMapMapper roleMapMapper = mock(AdminUserRoleMapMapper.class);
        AdminRoleMapper roleMapper = mock(AdminRoleMapper.class);
        IAuthService authService = mock(IAuthService.class);
        AdminUserRoleMapServiceImpl service = new AdminUserRoleMapServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", roleMapMapper);
        ReflectionTestUtils.setField(service, "adminRoleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "authService", authService);

        TRole superAdminRole = new TRole();
        superAdminRole.setId(9L);
        superAdminRole.setRoleId(900L);
        superAdminRole.setName("SUPER_ADMIN");
        TUserRoleMap roleMap = new TUserRoleMap();
        roleMap.setId(1);
        roleMap.setUserId(targetUserId);
        roleMap.setRoleId(900L);

        when(roleMapMapper.selectById(1)).thenReturn(roleMap);
        when(roleMapMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectOne(any())).thenReturn(superAdminRole);
        when(roleMapper.selectList(any())).thenReturn(List.of(superAdminRole));
        when(authService.queryValidatedUser())
                .thenReturn(AuthUserEntity.builder().userId(actorUserId).build());

        return new RoleMapFixture(service, roleMapMapper);
    }

    private record RoleMapFixture(
            AdminUserRoleMapServiceImpl service,
            AdminUserRoleMapMapper roleMapMapper) {
    }
}
