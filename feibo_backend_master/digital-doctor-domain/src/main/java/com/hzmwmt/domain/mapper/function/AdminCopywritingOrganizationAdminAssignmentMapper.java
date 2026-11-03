package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.entity.function.TCopywritingOrganizationAdminAssignment;
import com.hzmwmt.domain.vo.AdminCopywritingOrganizationAdminAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewOrganizationAdminOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminCopywritingOrganizationAdminAssignmentMapper
        extends BaseMapper<TCopywritingOrganizationAdminAssignment> {

    List<Long> selectUserIdsForUpdate(@Param("userIds") List<Long> userIds);

    List<Long> selectActiveOrganizationAdminUserIdsForUpdate(@Param("adminUserIds") List<Long> adminUserIds);

    List<Long> selectAdminIdsByAccountUserIdForUpdate(@Param("accountUserId") Long accountUserId);

    int deleteByAccountUserId(@Param("accountUserId") Long accountUserId);

    int insertAssignments(
            @Param("accountUserId") Long accountUserId,
            @Param("adminUserIds") List<Long> adminUserIds);

    int releaseClaimedTasks(
            @Param("accountUserId") Long accountUserId,
            @Param("adminUserIds") List<Long> adminUserIds);

    List<AdminCopywritingOrganizationAdminAssignmentVO> selectAssignmentsByAccountUserIds(
            @Param("accountUserIds") List<Long> accountUserIds);

    List<AdminReviewOrganizationAdminOptionVO> selectAdminOptions(
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    IPage<AdminReviewAccountOptionVO> selectAccountOptions(
            Page<AdminReviewAccountOptionVO> page,
            @Param("keyword") String keyword);
}
