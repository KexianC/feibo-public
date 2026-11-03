package com.hzmwmt.security;

import com.hzmwmt.domain.dto.RolePermissionSnapshotDTO;
import com.hzmwmt.domain.dto.RolePermissionUpdateDTO;
import com.hzmwmt.domain.entity.role.TPermission;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.entity.role.TRolePermissionMap;
import com.hzmwmt.domain.mapper.role.AdminPermissionMapper;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.mapper.role.AdminRolePermissionMapMapper;
import com.hzmwmt.domain.service.role.impl.AdminPermissionServiceImpl;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPermissionServiceSecurityTest {

    private AdminPermissionMapper permissionMapper;
    private AdminRolePermissionMapMapper rolePermissionMapper;
    private AdminRoleMapper roleMapper;
    private AdminPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        permissionMapper = mock(AdminPermissionMapper.class);
        rolePermissionMapper = mock(AdminRolePermissionMapMapper.class);
        roleMapper = mock(AdminRoleMapper.class);
        service = new AdminPermissionServiceImpl(permissionMapper, rolePermissionMapper, roleMapper);
    }

    @Test
    void replacesValidatedAssignmentsInsideVersionedSnapshot() {
        TRole role = role();
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of(101L));
        when(permissionMapper.selectByPermissionIds(anyCollection()))
                .thenReturn(List.of(permission(101L), permission(102L)));
        when(rolePermissionMapper.deleteByRoleId(1001L)).thenReturn(1);
        when(rolePermissionMapper.insert(any(TRolePermissionMap.class))).thenReturn(1);

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        RolePermissionUpdateDTO update = update(initial.getVersion(), List.of(102L, 101L), false);
        RolePermissionSnapshotDTO result = service.replaceRolePermissions(1L, update);

        assertThat(result.getPermissionIds()).containsExactly(101L, 102L);
        assertThat(result.getVersion()).isNotEqualTo(initial.getVersion());
        ArgumentCaptor<TRolePermissionMap> mapping = ArgumentCaptor.forClass(TRolePermissionMap.class);
        verify(rolePermissionMapper, org.mockito.Mockito.times(2)).insert(mapping.capture());
        assertThat(mapping.getAllValues())
                .extracting(TRolePermissionMap::getPermissionId)
                .containsExactly(101L, 102L);
    }

    @Test
    void staleVersionCannotOverwriteConcurrentUpdate() {
        TRole role = role();
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L))
                .thenReturn(List.of(101L), List.of(102L));

        RolePermissionSnapshotDTO stale = service.getRolePermissions(1L);
        RolePermissionUpdateDTO update = update(stale.getVersion(), List.of(101L), false);

        assertThatThrownBy(() -> service.replaceRolePermissions(1L, update))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode());
        verify(rolePermissionMapper, never()).deleteByRoleId(any());
        verify(rolePermissionMapper, never()).insert(any(TRolePermissionMap.class));
    }

    @Test
    void emptySelectionRequiresExplicitConfirmation() {
        TRole role = role();
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of(101L));

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        RolePermissionUpdateDTO update = update(initial.getVersion(), List.of(), false);

        assertThatThrownBy(() -> service.replaceRolePermissions(1L, update))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ResponseCode.ILLEGAL_PARAMETER.getCode());
        verify(rolePermissionMapper, never()).deleteByRoleId(any());
    }

    @Test
    void confirmedEmptySelectionCanBeApplied() {
        TRole role = role();
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of(101L));
        when(rolePermissionMapper.deleteByRoleId(1001L)).thenReturn(1);

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        RolePermissionSnapshotDTO result = service.replaceRolePermissions(
                1L,
                update(initial.getVersion(), List.of(), true));

        assertThat(result.getPermissionIds()).isEmpty();
        verify(rolePermissionMapper).deleteByRoleId(1001L);
        verify(rolePermissionMapper, never()).insert(any(TRolePermissionMap.class));
    }

    @Test
    void unknownPermissionCannotReachDeletePhase() {
        TRole role = role();
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of(101L));
        when(permissionMapper.selectByPermissionIds(List.of(999L))).thenReturn(List.of());

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        assertThatThrownBy(() -> service.replaceRolePermissions(
                1L,
                update(initial.getVersion(), List.of(999L), false)))
                .isInstanceOf(AppException.class);

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
    }

    @Test
    void duplicatePermissionIdsAreRejectedBeforeLocking() {
        RolePermissionUpdateDTO request = update("a".repeat(64), List.of(101L, 101L), false);

        assertThatThrownBy(() -> service.replaceRolePermissions(1L, request))
                .isInstanceOf(AppException.class);
        verify(roleMapper, never()).selectByDatabaseIdForUpdate(any());
    }

    @Test
    void permissionCodesCannotInjectSpringRoleAuthorities() {
        when(permissionMapper.selectCodesByUserId(1001L)).thenReturn(List.of(
                "admin.console",
                " ROLE_SUPER_ADMIN ",
                "role_admin",
                "content.manage"));

        assertThat(service.getPermissionCodesByUserId(1001L))
                .containsExactly("admin.console", "content.manage");
    }

    @Test
    void highRiskCapabilityCannotBeAssignedToOrdinaryAdminRole() {
        TRole role = role();
        TPermission identityManage = permission(104L);
        identityManage.setCode("identity.manage");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of());
        when(permissionMapper.selectByPermissionIds(List.of(104L)))
                .thenReturn(List.of(identityManage));

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        assertThatThrownBy(() -> service.replaceRolePermissions(
                1L,
                update(initial.getVersion(), List.of(104L), false)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo(ResponseCode.FORBIDDEN.getCode());

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
    }

    @Test
    void protectedSuperAdminRoleCanStillReceiveHighRiskCapability() {
        TRole role = role();
        role.setName("SUPERADMIN");
        TPermission identityManage = permission(104L);
        identityManage.setCode("identity.manage");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.selectByDatabaseIdForUpdate(1L)).thenReturn(role);
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1001L)).thenReturn(List.of());
        when(permissionMapper.selectByPermissionIds(List.of(104L)))
                .thenReturn(List.of(identityManage));
        when(rolePermissionMapper.deleteByRoleId(1001L)).thenReturn(0);
        when(rolePermissionMapper.insert(any(TRolePermissionMap.class))).thenReturn(1);

        RolePermissionSnapshotDTO initial = service.getRolePermissions(1L);
        assertThat(service.replaceRolePermissions(
                1L,
                update(initial.getVersion(), List.of(104L), false)).getPermissionIds())
                .containsExactly(104L);
    }

    private static TRole role() {
        TRole role = new TRole();
        role.setId(1L);
        role.setRoleId(1001L);
        role.setName("ADMIN");
        return role;
    }

    private static TPermission permission(Long id) {
        TPermission permission = new TPermission();
        permission.setPermissionId(id);
        permission.setCode("permission.p" + id);
        return permission;
    }

    private static RolePermissionUpdateDTO update(
            String version,
            List<Long> permissionIds,
            boolean confirmEmpty) {
        RolePermissionUpdateDTO request = new RolePermissionUpdateDTO();
        request.setVersion(version);
        request.setPermissionIds(permissionIds);
        request.setConfirmEmpty(confirmEmpty);
        return request;
    }
}
