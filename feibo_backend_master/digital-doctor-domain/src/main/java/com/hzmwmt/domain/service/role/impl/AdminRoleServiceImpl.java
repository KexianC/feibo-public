package com.hzmwmt.domain.service.role.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.service.role.AdminRoleService;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.RoleCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, TRole> implements AdminRoleService {
    private static final Set<String> PROTECTED_SYSTEM_ROLES =
            Set.of(
                    RoleCode.SUPER_ADMIN,
                    RoleCode.ADMIN,
                    RoleCode.EXPERT,
                    RoleCode.ORG_ADMIN,
                    RoleCode.DOCTOR,
                    RoleCode.DOCTOR_ASSISTANT);

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "新增", description = "创建角色", recordResult = false, critical = true)
    public boolean save(TRole role) {
        if (role == null) {
            return false;
        }
        role.setName(normalizeRoleCode(role.getName()));
        rejectProtectedRoleTarget(role.getName());
        validateDescription(role.getDescription());
        role.setId(null);
        role.setRoleId(IdWorker.getId());
        role.setCreateTime(null);
        role.setUpdateTime(null);
        return super.save(role);
    }

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "修改", description = "更新角色", recordResult = false, critical = true)
    public boolean updateById(TRole role) {
        if (role == null || role.getId() == null) {
            return false;
        }
        TRole existingRole = super.getById(role.getId());
        if (existingRole != null
                && role.getRoleId() != null
                && !Objects.equals(existingRole.getRoleId(), role.getRoleId())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Role business ID is immutable");
        }
        if (role.getName() != null) {
            String targetRoleCode = normalizeRoleCode(role.getName());
            if (existingRole != null && isProtectedSystemRole(existingRole.getName())) {
                String existingRoleCode = normalizeRoleCode(existingRole.getName());
                if (!existingRoleCode.equals(targetRoleCode)) {
                    throw protectedRoleMutation();
                }
            } else {
                rejectProtectedRoleTarget(targetRoleCode);
            }
            role.setName(targetRoleCode);
        }
        validateDescription(role.getDescription());
        TRole safeUpdate = new TRole();
        safeUpdate.setId(role.getId());
        safeUpdate.setName(role.getName());
        safeUpdate.setDescription(role.getDescription());
        return super.updateById(safeUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "删除", description = "删除角色", recordResult = false, critical = true)
    public boolean removeById(Serializable id) {
        rejectProtectedRoleIds(List.of(id));
        return super.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "删除", description = "批量删除角色", recordResult = false, critical = true)
    public boolean removeBatchByIds(Collection<?> list) {
        rejectProtectedRoleIds(list);
        return super.removeBatchByIds(list);
    }

    public TRole getById(Long id) {
        return super.getById(id);
    }

    public List<TRole> list() {
        return super.list();
    }

    private void rejectProtectedRoleIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .map(id -> Long.valueOf(String.valueOf(id)))
                .toList();
        boolean containsProtectedRole = super.listByIds(normalizedIds).stream()
                .anyMatch(role -> isProtectedSystemRole(role.getName()));
        if (containsProtectedRole) {
            throw protectedRoleMutation();
        }
    }

    private void rejectProtectedRoleTarget(String roleName) {
        if (isProtectedSystemRole(roleName)) {
            throw protectedRoleMutation();
        }
    }

    private String normalizeRoleCode(String roleName) {
        return RoleCode.canonicalize(roleName)
                .orElseThrow(() -> new AppException(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "Role name must be a valid authority code"));
    }

    private boolean isProtectedSystemRole(String roleName) {
        return RoleCode.canonicalize(roleName)
                .map(PROTECTED_SYSTEM_ROLES::contains)
                .orElse(false);
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 255) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
    }

    private AppException protectedRoleMutation() {
        return new AppException(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "Built-in system roles cannot be renamed or deleted");
    }
}
