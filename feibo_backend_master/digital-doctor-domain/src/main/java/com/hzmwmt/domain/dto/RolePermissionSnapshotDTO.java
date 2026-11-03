package com.hzmwmt.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionSnapshotDTO {
    /** t_role.id, used by the management endpoint. */
    private Long roleDatabaseId;
    /** t_role.role_id, used by t_role_permission_map. */
    private Long roleId;
    private List<Long> permissionIds;
    /** Opaque SHA-256 version of the locked assignment set. */
    private String version;
}
