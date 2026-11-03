package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.mapper.function.AdminCopywritingExpertAssignmentMapper;
import com.hzmwmt.domain.service.function.AdminCopywritingExpertAssignmentService;
import com.hzmwmt.domain.vo.AdminCopywritingExpertAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewExpertOptionVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminCopywritingExpertAssignmentServiceImpl implements AdminCopywritingExpertAssignmentService {

    private static final int MAX_ACCOUNTS_PER_QUERY = 200;
    private static final int MAX_EXPERTS_PER_ACCOUNT = 100;
    private static final int EXPERT_OPTION_LIMIT = 100;

    @Resource
    private AdminCopywritingExpertAssignmentMapper assignmentMapper;

    @Override
    public IPage<AdminReviewAccountOptionVO> queryAccounts(Integer page, Integer size, String keyword) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        return assignmentMapper.selectAccountOptions(
                new Page<>(normalizedPage, normalizedSize),
                normalizedKeyword);
    }

    @Override
    public List<AdminCopywritingExpertAssignmentVO> queryAssignments(List<Long> accountUserIds) {
        List<Long> normalizedAccountIds = normalizeIds(accountUserIds, MAX_ACCOUNTS_PER_QUERY);
        if (normalizedAccountIds.isEmpty()) {
            return List.of();
        }
        return assignmentMapper.selectAssignmentsByAccountUserIds(normalizedAccountIds);
    }

    @Override
    public List<AdminReviewExpertOptionVO> queryExpertOptions(String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        return assignmentMapper.selectExpertOptions(normalizedKeyword, EXPERT_OPTION_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdminCopywritingExpertAssignmentVO> replaceAssignments(
            Long accountUserId,
            List<Long> expertUserIds) {
        if (accountUserId == null || accountUserId <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        List<Long> normalizedExpertIds = normalizeIds(expertUserIds, MAX_EXPERTS_PER_ACCOUNT);

        List<Long> userIdsToLock = java.util.stream.Stream
                .concat(java.util.stream.Stream.of(accountUserId), normalizedExpertIds.stream())
                .distinct()
                .sorted()
                .toList();
        Set<Long> lockedUserIds = new LinkedHashSet<>(assignmentMapper.selectUserIdsForUpdate(userIdsToLock));
        if (!lockedUserIds.contains(accountUserId)) {
            throw new AppException(ResponseCode.E0005);
        }

        if (!normalizedExpertIds.isEmpty()) {
            Set<Long> activeExpertIds = new LinkedHashSet<>(
                    assignmentMapper.selectActiveExpertUserIdsForUpdate(normalizedExpertIds));
            if (!activeExpertIds.containsAll(normalizedExpertIds)) {
                throw new AppException(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "Only active accounts with the EXPERT role can be assigned");
            }
        }

        Set<Long> existingExpertIds = new LinkedHashSet<>(
                assignmentMapper.selectExpertIdsByAccountUserIdForUpdate(accountUserId));
        Set<Long> removedExpertIds = new LinkedHashSet<>(existingExpertIds);
        removedExpertIds.removeAll(normalizedExpertIds);

        assignmentMapper.deleteByAccountUserId(accountUserId);
        if (!normalizedExpertIds.isEmpty()) {
            assignmentMapper.insertAssignments(accountUserId, normalizedExpertIds);
        }
        if (!removedExpertIds.isEmpty()) {
            assignmentMapper.releaseClaimedTasks(accountUserId, List.copyOf(removedExpertIds));
        }

        return assignmentMapper.selectAssignmentsByAccountUserIds(List.of(accountUserId));
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
