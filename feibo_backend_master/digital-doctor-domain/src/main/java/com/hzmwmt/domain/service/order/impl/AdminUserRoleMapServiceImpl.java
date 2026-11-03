package com.hzmwmt.domain.service.order.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import com.hzmwmt.domain.entity.role.TRole;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalFlowMapper;
import com.hzmwmt.domain.mapper.role.AdminRoleMapper;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.mapper.role.AdminUserRoleMapMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.order.AdminUserRoleMapService;
import com.hzmwmt.domain.vo.role.AdminUserRoleMapVO;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.RoleCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserRoleMapServiceImpl extends ServiceImpl<AdminUserRoleMapMapper, TUserRoleMap> implements AdminUserRoleMapService {
    @Resource
    private AdminRoleMapper adminRoleMapper;
    @Resource
    private AdminCopywritingApprovalFlowMapper approvalFlowMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private IAuthService authService;

    @Override
    public IPage<AdminUserRoleMapVO> selectUserRoleMapVOPage(Page<AdminUserRoleMapVO> page, Wrapper<AdminUserRoleMapVO> queryWrapper) {
        // 调用Mapper的注解SQL方法
        return baseMapper.selectUserRoleMapPage(page, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "新增", description = "授予用户角色", recordResult = false, critical = true)
    public boolean save(TUserRoleMap userRoleMap) {
        if (userRoleMap == null || userRoleMap.getUserId() == null || userRoleMap.getRoleId() == null) {
            return false;
        }
        validateReferences(userRoleMap.getUserId(), userRoleMap.getRoleId());
        rejectSelfPrivilegedRoleChange(userRoleMap);
        lockIfSuperAdminRole(userRoleMap.getRoleId());
        long existing = count(new LambdaQueryWrapper<TUserRoleMap>()
                .eq(TUserRoleMap::getUserId, userRoleMap.getUserId())
                .eq(TUserRoleMap::getRoleId, userRoleMap.getRoleId()));
        if (existing > 0) {
            return true;
        }
        userRoleMap.setId(null);
        userRoleMap.setCreateTime(null);
        return super.save(userRoleMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "删除", description = "撤销用户角色", recordResult = false, critical = true)
    public boolean removeById(Serializable id) {
        TUserRoleMap roleMap = getById(id);
        if (roleMap == null) {
            return false;
        }
        rejectSelfPrivilegedRoleChange(roleMap);
        assertSuperAdminRevocationKeepsActiveAdministrator(roleMap, Set.of(roleMap.getId()));
        Set<Long> expertRoleIds = findExpertRoleIds();
        Set<Long> organizationAdminRoleIds = findOrganizationAdminRoleIds();
        boolean removed = super.removeById(id);
        if (removed && expertRoleIds.contains(roleMap.getRoleId())) {
            releaseExpertTasksIfRoleRemoved(roleMap.getUserId(), expertRoleIds);
        }
        if (removed && organizationAdminRoleIds.contains(roleMap.getRoleId())) {
            releaseOrganizationTasksIfRoleRemoved(roleMap.getUserId(), organizationAdminRoleIds);
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "删除", description = "批量撤销用户角色", recordResult = false, critical = true)
    public boolean removeBatchByIds(Collection<?> idList) {
        if (idList == null || idList.isEmpty()) {
            return false;
        }
        List<Serializable> serializableIds = idList.stream()
                .filter(Serializable.class::isInstance)
                .map(Serializable.class::cast)
                .toList();
        if (serializableIds.size() != idList.size()) {
            return false;
        }
        List<TUserRoleMap> roleMaps = listByIds(serializableIds);
        for (TUserRoleMap roleMap : roleMaps) {
            rejectSelfPrivilegedRoleChange(roleMap);
        }
        assertBatchSuperAdminRevocationKeepsActiveAdministrator(roleMaps, serializableIds);
        Set<Long> expertRoleIds = findExpertRoleIds();
        Set<Long> organizationAdminRoleIds = findOrganizationAdminRoleIds();
        Set<Long> affectedExpertUserIds = new HashSet<>();
        Set<Long> affectedOrganizationAdminUserIds = new HashSet<>();
        for (TUserRoleMap roleMap : roleMaps) {
            if (roleMap != null && expertRoleIds.contains(roleMap.getRoleId())) {
                affectedExpertUserIds.add(roleMap.getUserId());
            }
            if (roleMap != null && organizationAdminRoleIds.contains(roleMap.getRoleId())) {
                affectedOrganizationAdminUserIds.add(roleMap.getUserId());
            }
        }
        boolean removed = super.removeBatchByIds(serializableIds);
        if (removed) {
            for (Long userId : affectedExpertUserIds) {
                releaseExpertTasksIfRoleRemoved(userId, expertRoleIds);
            }
            for (Long userId : affectedOrganizationAdminUserIds) {
                releaseOrganizationTasksIfRoleRemoved(userId, organizationAdminRoleIds);
            }
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(operationType = "修改", description = "变更用户角色", recordResult = false, critical = true)
    public boolean updateById(TUserRoleMap userRoleMap) {
        if (userRoleMap == null || userRoleMap.getId() == null) {
            return false;
        }
        TUserRoleMap existingRoleMap = getById(userRoleMap.getId());
        if (existingRoleMap == null) {
            return false;
        }
        Long nextUserId = userRoleMap.getUserId() == null
                ? existingRoleMap.getUserId()
                : userRoleMap.getUserId();
        Long nextRoleId = userRoleMap.getRoleId() == null
                ? existingRoleMap.getRoleId()
                : userRoleMap.getRoleId();
        validateReferences(nextUserId, nextRoleId);
        rejectSelfPrivilegedRoleChange(existingRoleMap);
        TUserRoleMap nextRoleMap = new TUserRoleMap();
        nextRoleMap.setUserId(nextUserId);
        nextRoleMap.setRoleId(nextRoleId);
        rejectSelfPrivilegedRoleChange(nextRoleMap);
        lockIfSuperAdminRole(nextRoleId);
        if (!Objects.equals(existingRoleMap.getUserId(), nextUserId)
                || !Objects.equals(existingRoleMap.getRoleId(), nextRoleId)) {
            assertSuperAdminRevocationKeepsActiveAdministrator(
                    existingRoleMap,
                    Set.of(existingRoleMap.getId()));
            long duplicate = count(new LambdaQueryWrapper<TUserRoleMap>()
                    .eq(TUserRoleMap::getUserId, nextUserId)
                    .eq(TUserRoleMap::getRoleId, nextRoleId)
                    .ne(TUserRoleMap::getId, existingRoleMap.getId()));
            if (duplicate > 0) {
                throw new AppException(
                        ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
                        "该用户已拥有目标角色");
            }
        }
        Set<Long> expertRoleIds = findExpertRoleIds();
        Set<Long> organizationAdminRoleIds = findOrganizationAdminRoleIds();
        boolean updated = super.updateById(userRoleMap);
        if (updated && expertRoleIds.contains(existingRoleMap.getRoleId())) {
            releaseExpertTasksIfRoleRemoved(existingRoleMap.getUserId(), expertRoleIds);
        }
        if (updated && organizationAdminRoleIds.contains(existingRoleMap.getRoleId())) {
            releaseOrganizationTasksIfRoleRemoved(existingRoleMap.getUserId(), organizationAdminRoleIds);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assertCanDisableUser(Long userId) {
        if (userId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        lockSuperAdminInvariant();
        Set<Long> activeSuperAdminUserIds = new HashSet<>(
                baseMapper.selectActiveSuperAdminUserIdsForUpdate());
        if (activeSuperAdminUserIds.contains(userId) && activeSuperAdminUserIds.size() <= 1) {
            throw lastSuperAdminViolation();
        }
    }

    private void validateReferences(Long userId, Long roleId) {
        Long users = adminUserMapper.selectCount(new LambdaQueryWrapper<TUser>()
                .eq(TUser::getUserId, userId));
        Long roles = adminRoleMapper.selectCount(new LambdaQueryWrapper<TRole>()
                .eq(TRole::getRoleId, roleId));
        if (users == null || users != 1L || roles == null || roles != 1L) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "用户或角色不存在");
        }
    }

    private void rejectSelfPrivilegedRoleChange(TUserRoleMap roleMap) {
        if (roleMap == null || roleMap.getUserId() == null || roleMap.getRoleId() == null) {
            return;
        }
        Long actorUserId = authService.queryValidatedUser().getUserId();
        if (!Objects.equals(actorUserId, roleMap.getUserId())) {
            return;
        }
        String roleCode = findCanonicalRoleCode(roleMap.getRoleId());
        if (RoleCode.ADMIN.equals(roleCode) || RoleCode.SUPER_ADMIN.equals(roleCode)) {
            throw new AppException(
                    ResponseCode.FORBIDDEN.getCode(),
                    "禁止修改自己的特权角色");
        }
    }

    private void assertSuperAdminRevocationKeepsActiveAdministrator(
            TUserRoleMap roleMap,
            Set<? extends Serializable> mappingIdsBeingRemoved) {
        if (roleMap == null || !RoleCode.SUPER_ADMIN.equals(findCanonicalRoleCode(roleMap.getRoleId()))) {
            return;
        }
        Set<Long> superRoleIds = lockSuperAdminInvariant();
        Set<Long> activeSuperAdminUserIds = new HashSet<>(
                baseMapper.selectActiveSuperAdminUserIdsForUpdate());
        if (!activeSuperAdminUserIds.contains(roleMap.getUserId())) {
            return;
        }
        long remainingMappings = count(new LambdaQueryWrapper<TUserRoleMap>()
                .eq(TUserRoleMap::getUserId, roleMap.getUserId())
                .in(TUserRoleMap::getRoleId, superRoleIds)
                .notIn(TUserRoleMap::getId, mappingIdsBeingRemoved));
        if (remainingMappings == 0 && activeSuperAdminUserIds.size() <= 1) {
            throw lastSuperAdminViolation();
        }
    }

    private void assertBatchSuperAdminRevocationKeepsActiveAdministrator(
            List<TUserRoleMap> roleMaps,
            Collection<? extends Serializable> mappingIdsBeingRemoved) {
        if (roleMaps == null || roleMaps.isEmpty()) {
            return;
        }
        Set<Long> superRoleIds = lockSuperAdminInvariant();
        Set<Long> activeSuperAdminUserIds = new HashSet<>(
                baseMapper.selectActiveSuperAdminUserIdsForUpdate());
        Set<Long> candidateUserIds = roleMaps.stream()
                .filter(Objects::nonNull)
                .filter(roleMap -> superRoleIds.contains(roleMap.getRoleId()))
                .map(TUserRoleMap::getUserId)
                .filter(activeSuperAdminUserIds::contains)
                .collect(Collectors.toSet());
        long usersLosingSuperAdmin = candidateUserIds.stream()
                .filter(userId -> count(new LambdaQueryWrapper<TUserRoleMap>()
                        .eq(TUserRoleMap::getUserId, userId)
                        .in(TUserRoleMap::getRoleId, superRoleIds)
                        .notIn(TUserRoleMap::getId, mappingIdsBeingRemoved)) == 0)
                .count();
        if (activeSuperAdminUserIds.size() - usersLosingSuperAdmin < 1) {
            throw lastSuperAdminViolation();
        }
    }

    private void lockIfSuperAdminRole(Long roleId) {
        if (RoleCode.SUPER_ADMIN.equals(findCanonicalRoleCode(roleId))) {
            lockSuperAdminInvariant();
        }
    }

    private Set<Long> lockSuperAdminInvariant() {
        List<Long> roleIds = adminRoleMapper.selectSuperAdminRoleIdsForUpdate();
        return roleIds == null
                ? Set.of()
                : roleIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String findCanonicalRoleCode(Long roleId) {
        if (roleId == null) {
            return null;
        }
        List<TRole> roles = adminRoleMapper.selectList(new LambdaQueryWrapper<TRole>()
                .eq(TRole::getRoleId, roleId));
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        if (roles.size() != 1) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Role business ID is not unique");
        }
        return RoleCode.canonicalize(roles.get(0).getName()).orElse(null);
    }

    private AppException lastSuperAdminViolation() {
        return new AppException(
                ResponseCode.FORBIDDEN.getCode(),
                "必须至少保留一个活跃的超级管理员");
    }

    private Set<Long> findExpertRoleIds() {
        List<TRole> roles = adminRoleMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> roleIds = new HashSet<>();
        for (TRole role : roles) {
            if (role != null && role.getRoleId() != null && "EXPERT".equalsIgnoreCase(role.getName())) {
                roleIds.add(role.getRoleId());
            }
        }
        return roleIds;
    }

    private Set<Long> findOrganizationAdminRoleIds() {
        List<TRole> roles = adminRoleMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> roleIds = new HashSet<>();
        for (TRole role : roles) {
            if (role == null || role.getRoleId() == null || role.getName() == null) {
                continue;
            }
            String roleName = role.getName().toUpperCase();
            if ("ORG_ADMIN".equals(roleName) || "ADMIN".equals(roleName)) {
                roleIds.add(role.getRoleId());
            }
        }
        return roleIds;
    }

    private void releaseExpertTasksIfRoleRemoved(Long userId, Set<Long> expertRoleIds) {
        if (userId == null || expertRoleIds.isEmpty()) {
            return;
        }
        long remainingExpertRoles = count(new LambdaQueryWrapper<TUserRoleMap>()
                .eq(TUserRoleMap::getUserId, userId)
                .in(TUserRoleMap::getRoleId, expertRoleIds));
        if (remainingExpertRoles > 0) {
            return;
        }
        releaseExpertTasks(userId);
    }

    private void releaseExpertTasks(Long userId) {
        approvalFlowMapper.update(null, new LambdaUpdateWrapper<TCopywritingApprovalFlow>()
                .set(TCopywritingApprovalFlow::getCurrentProcessorId, null)
                .eq(TCopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.EXPERT_PENDING.getCode())
                .eq(TCopywritingApprovalFlow::getCurrentProcessorId, userId));
    }

    private void releaseOrganizationTasksIfRoleRemoved(Long userId, Set<Long> organizationAdminRoleIds) {
        if (userId == null || organizationAdminRoleIds.isEmpty()) {
            return;
        }
        long remainingOrganizationAdminRoles = count(new LambdaQueryWrapper<TUserRoleMap>()
                .eq(TUserRoleMap::getUserId, userId)
                .in(TUserRoleMap::getRoleId, organizationAdminRoleIds));
        if (remainingOrganizationAdminRoles > 0) {
            return;
        }
        approvalFlowMapper.update(null, new LambdaUpdateWrapper<TCopywritingApprovalFlow>()
                .set(TCopywritingApprovalFlow::getCurrentProcessorId, null)
                .eq(TCopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())
                .eq(TCopywritingApprovalFlow::getCurrentProcessorId, userId));
    }
}
