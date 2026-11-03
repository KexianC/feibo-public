package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.mapper.function.AdminCopywritingOrganizationAdminAssignmentMapper;
import com.hzmwmt.domain.service.function.AdminCopywritingOrganizationAdminAssignmentService;
import com.hzmwmt.domain.vo.AdminCopywritingOrganizationAdminAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewOrganizationAdminOptionVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminCopywritingOrganizationAdminAssignmentServiceImpl
        implements AdminCopywritingOrganizationAdminAssignmentService {

    private static final int MAX_ACCOUNTS_PER_QUERY = 200;
    private static final int MAX_ADMINS_PER_ACCOUNT = 100;
    private static final int ADMIN_OPTION_LIMIT = 100;

    @Resource
    private AdminCopywritingOrganizationAdminAssignmentMapper assignmentMapper;

    @Override
    public IPage<AdminReviewAccountOptionVO> queryAccounts(Integer page, Integer size, String keyword) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        String normalizedKeyword = normalizeKeyword(keyword);
        return assignmentMapper.selectAccountOptions(
                new Page<>(normalizedPage, normalizedSize),
                normalizedKeyword);
    }

    @Override
    public List<AdminCopywritingOrganizationAdminAssignmentVO> queryAssignments(List<Long> accountUserIds) {
        List<Long> normalizedAccountIds = normalizeIds(accountUserIds, MAX_ACCOUNTS_PER_QUERY);
        if (normalizedAccountIds.isEmpty()) {
            return List.of();
        }
        return assignmentMapper.selectAssignmentsByAccountUserIds(normalizedAccountIds);
    }

    @Override
    public List<AdminReviewOrganizationAdminOptionVO> queryAdminOptions(String keyword) {
        return assignmentMapper.selectAdminOptions(normalizeKeyword(keyword), ADMIN_OPTION_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminCopywritingOrganizationAdminAssignmentVO> replaceAssignments(
            Long accountUserId,
            List<Long> adminUserIds) {
        if (accountUserId == null || accountUserId <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        List<Long> normalizedAdminIds = normalizeIds(adminUserIds, MAX_ADMINS_PER_ACCOUNT);

        List<Long> userIdsToLock = java.util.stream.Stream
                .concat(java.util.stream.Stream.of(accountUserId), normalizedAdminIds.stream())
                .distinct()
                .sorted()
                .toList();
        Set<Long> lockedUserIds = new LinkedHashSet<>(assignmentMapper.selectUserIdsForUpdate(userIdsToLock));
        if (!lockedUserIds.contains(accountUserId)) {
            throw new AppException(ResponseCode.E0005);
        }

        if (!normalizedAdminIds.isEmpty()) {
            Set<Long> activeAdminIds = new LinkedHashSet<>(
                    assignmentMapper.selectActiveOrganizationAdminUserIdsForUpdate(normalizedAdminIds));
            if (!activeAdminIds.containsAll(normalizedAdminIds)) {
                throw new AppException(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "Only active accounts with the ORG_ADMIN or ADMIN role can be assigned");
            }
        }

        Set<Long> existingAdminIds = new LinkedHashSet<>(
                assignmentMapper.selectAdminIdsByAccountUserIdForUpdate(accountUserId));
        Set<Long> removedAdminIds = new LinkedHashSet<>(existingAdminIds);
        removedAdminIds.removeAll(normalizedAdminIds);

        assignmentMapper.deleteByAccountUserId(accountUserId);
        if (!normalizedAdminIds.isEmpty()) {
            assignmentMapper.insertAssignments(accountUserId, normalizedAdminIds);
        }
        if (!removedAdminIds.isEmpty()) {
            assignmentMapper.releaseClaimedTasks(accountUserId, List.copyOf(removedAdminIds));
        }

        return assignmentMapper.selectAssignmentsByAccountUserIds(List.of(accountUserId));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalizedKeyword = keyword.trim();
        return normalizedKeyword.isEmpty() ? null : normalizedKeyword;
    }

    private List<Long> normalizeIds(List<Long> ids, int maximumSize) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        List<Long> normalizedIds = ids.stream().distinct().toList();
        if (normalizedIds.size() > maximumSize) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        return normalizedIds;
    }
}
