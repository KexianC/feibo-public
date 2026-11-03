package com.hzmwmt.domain.controller.role;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.PermissionCatalogItemDTO;
import com.hzmwmt.domain.dto.RolePermissionSnapshotDTO;
import com.hzmwmt.domain.dto.RolePermissionUpdateDTO;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin")
@Tag(name = "能力权限管理", description = "超级管理员维护角色的能力权限")
public class AdminPermissionController {

    private final AdminPermissionService permissionService;

    public AdminPermissionController(AdminPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "查询权限目录")
    @GetMapping("/permission")
    public Result<List<PermissionCatalogItemDTO>> listPermissions() {
        return Result.success(permissionService.listPermissions());
    }

    @Operation(summary = "读取角色当前权限及并发版本")
    @GetMapping("/role/{roleDatabaseId}/permissions")
    public Result<RolePermissionSnapshotDTO> getRolePermissions(
            @PathVariable @Positive Long roleDatabaseId) {
        return Result.success(permissionService.getRolePermissions(roleDatabaseId));
    }

    @Operation(summary = "按版本全量替换角色权限")
    @PutMapping("/role/{roleDatabaseId}/permissions")
    public Result<RolePermissionSnapshotDTO> replaceRolePermissions(
            @PathVariable @Positive Long roleDatabaseId,
            @Valid @RequestBody RolePermissionUpdateDTO request) {
        return Result.success(permissionService.replaceRolePermissions(roleDatabaseId, request));
    }
}
