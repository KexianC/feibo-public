package com.hzmwmt.domain.service.role.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hzmwmt.domain.dto.PermissionCatalogItemDTO;
import com.hzmwmt.domain.dto.RolePermissionSnapshotDTO;
import com.hzmwmt.domain.dto.RolePermissionUpdateDTO;
import com.hzmwmt.domain.entity.role.TPermission;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.entity.role.TRolePermissionMap;
import com.hzmwmt.domain.mapper.role.AdminPermissionMapper;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.mapper.role.AdminRolePermissionMapMapper;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.RoleCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private static final int MAX_PERMISSION_ASSIGNMENTS = 256;
    private static final Pattern CAPABILITY_CODE = Pattern.compile(
            "^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)+$");
    private static final Set<String> SUPER_ADMIN_ONLY_CAPABILITIES = Set.of(
            "user.manage",
            "identity.manage",
            "audit.read",
            "system.manage",
            "finance.manage");

    private final AdminPermissionMapper permissionMapper;
    private final AdminRolePermissionMapMapper rolePermissionMapMapper;
    private final AdminRoleMapper roleMapper;

    public AdminPermissionServiceImpl(
            AdminPermissionMapper permissionMapper,
            AdminRolePermissionMapMapper rolePermissionMapMapper,
            AdminRoleMapper roleMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapMapper = rolePermissionMapMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public List<PermissionCatalogItemDTO> listPermissions() {
        return permissionMapper.selectAllOrdered().stream()
                .map(this::toCatalogItem)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public RolePermissionSnapshotDTO getRolePermissions(Long roleDatabaseId) {
        TRole role = requireRole(roleMapper.selectById(requirePositiveRoleId(roleDatabaseId)));
        return snapshot(role, loadPermissionIds(role.getRoleId()));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(
            operationType = "修改",
            description = "替换角色能力权限",
            recordParams = true,
            recordResult = false,
            critical = true)
    public RolePermissionSnapshotDTO replaceRolePermissions(
            Long roleDatabaseId,
            RolePermissionUpdateDTO request) {
        validateRequest(request);

        TRole role = requireRole(roleMapper.selectByDatabaseIdForUpdate(
                requirePositiveRoleId(roleDatabaseId)));
        List<Long> currentPermissionIds = loadPermissionIds(role.getRoleId());
        String currentVersion = version(role.getRoleId(), currentPermissionIds);
        if (!MessageDigest.isEqual(
                currentVersion.getBytes(StandardCharsets.US_ASCII),
                request.getVersion().getBytes(StandardCharsets.US_ASCII))) {
            throw new AppException(
                    ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
                    "角色权限已被其他管理员更新，请刷新后重试");
        }

        List<Long> requestedPermissionIds = normalizePermissionIds(request.getPermissionIds());
        if (requestedPermissionIds.isEmpty() && !Boolean.TRUE.equals(request.getConfirmEmpty())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "清空角色权限必须显式确认");
        }
        requireAllPermissionsExist(role, requestedPermissionIds);

        rolePermissionMapMapper.deleteByRoleId(role.getRoleId());
        for (Long permissionId : requestedPermissionIds) {
            TRolePermissionMap mapping = new TRolePermissionMap();
            mapping.setId(IdWorker.getId());
            mapping.setRoleId(role.getRoleId());
            mapping.setPermissionId(permissionId);
            if (rolePermissionMapMapper.insert(mapping) != 1) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
        }
        return snapshot(role, requestedPermissionIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getPermissionCodesByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<String> codes;
        try {
            codes = permissionMapper.selectCodesByUserId(userId);
        } catch (RuntimeException exception) {
            throw new AuthorizationDataUnavailableException(exception);
        }
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .filter(code -> CAPABILITY_CODE.matcher(code).matches())
                .distinct()
                .sorted()
                .toList();
    }

    private void validateRequest(RolePermissionUpdateDTO request) {
        if (request == null
                || request.getVersion() == null
                || !request.getVersion().matches("^[0-9a-f]{64}$")
                || request.getPermissionIds() == null
                || request.getPermissionIds().size() > MAX_PERMISSION_ASSIGNMENTS) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        if (request.getPermissionIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(request.getPermissionIds());
        if (uniqueIds.size() != request.getPermissionIds().size()) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "权限 ID 不得重复");
        }
    }

    private List<Long> normalizePermissionIds(List<Long> permissionIds) {
        List<Long> normalized = new ArrayList<>(permissionIds);
        Collections.sort(normalized);
        return List.copyOf(normalized);
    }

    private void requireAllPermissionsExist(TRole role, List<Long> requestedPermissionIds) {
        if (requestedPermissionIds.isEmpty()) {
            return;
        }
        List<TPermission> permissions = permissionMapper.selectByPermissionIds(requestedPermissionIds);
        Set<Long> existingIds = permissions == null
                ? Set.of()
                : permissions.stream()
                        .map(TPermission::getPermissionId)
                        .collect(Collectors.toSet());
        if (existingIds.size() != requestedPermissionIds.size()
                || !existingIds.containsAll(requestedPermissionIds)) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "角色权限包含不存在的权限 ID");
        }
        if (permissions.stream().anyMatch(permission -> permission.getCode() == null
                || !CAPABILITY_CODE.matcher(permission.getCode()).matches())) {
            throw new AppException(
                    ResponseCode.MANAGE_ERROR.getCode(),
                    "权限目录配置无效");
        }
        boolean superAdminTarget = RoleCode.canonicalize(role.getName())
                .map(RoleCode.SUPER_ADMIN::equals)
                .orElse(false);
        if (!superAdminTarget && permissions.stream()
                .map(TPermission::getCode)
                .anyMatch(SUPER_ADMIN_ONLY_CAPABILITIES::contains)) {
            throw new AppException(
                    ResponseCode.FORBIDDEN.getCode(),
                    "该能力仅可分配给超级管理员角色");
        }
    }

    private List<Long> loadPermissionIds(Long roleId) {
        List<Long> permissionIds = rolePermissionMapMapper.selectPermissionIdsByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
    }

    private RolePermissionSnapshotDTO snapshot(TRole role, List<Long> permissionIds) {
        List<Long> immutableIds = List.copyOf(permissionIds);
        return RolePermissionSnapshotDTO.builder()
                .roleDatabaseId(role.getId())
                .roleId(role.getRoleId())
                .permissionIds(immutableIds)
                .version(version(role.getRoleId(), immutableIds))
                .build();
    }

    private String version(Long roleId, List<Long> permissionIds) {
        String canonical = roleId + ":" + permissionIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Long requirePositiveRoleId(Long roleDatabaseId) {
        if (roleDatabaseId == null || roleDatabaseId <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        return roleDatabaseId;
    }

    private TRole requireRole(TRole role) {
        if (role == null || role.getId() == null || role.getRoleId() == null) {
            throw new AppException(
                    ResponseCode.E0005.getCode(),
                    "角色不存在");
        }
        return role;
    }

    private PermissionCatalogItemDTO toCatalogItem(TPermission permission) {
        if (permission == null
                || permission.getPermissionId() == null
                || permission.getPermissionId() <= 0
                || permission.getCode() == null
                || !CAPABILITY_CODE.matcher(permission.getCode()).matches()) {
            throw new AppException(
                    ResponseCode.MANAGE_ERROR.getCode(),
                    "权限目录配置无效");
        }
        return PermissionCatalogItemDTO.builder()
                .id(permission.getPermissionId())
                .code(permission.getCode())
                .name(permission.getName())
                .description(permission.getDescription())
                .reserved(permission.getReserved())
                .build();
    }
}
