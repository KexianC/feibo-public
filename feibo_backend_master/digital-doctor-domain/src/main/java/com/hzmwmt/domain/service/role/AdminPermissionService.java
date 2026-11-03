package com.hzmwmt.domain.service.role;

import com.hzmwmt.domain.dto.PermissionCatalogItemDTO;
import com.hzmwmt.domain.dto.RolePermissionSnapshotDTO;
import com.hzmwmt.domain.dto.RolePermissionUpdateDTO;

import java.util.List;

public interface AdminPermissionService {
    List<PermissionCatalogItemDTO> listPermissions();

    RolePermissionSnapshotDTO getRolePermissions(Long roleDatabaseId);

    RolePermissionSnapshotDTO replaceRolePermissions(
            Long roleDatabaseId,
            RolePermissionUpdateDTO request);

    List<String> getPermissionCodesByUserId(Long userId);
}
