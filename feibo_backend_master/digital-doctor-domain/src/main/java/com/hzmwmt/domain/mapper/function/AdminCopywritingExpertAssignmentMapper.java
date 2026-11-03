package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.entity.function.TCopywritingExpertAssignment;
import com.hzmwmt.domain.vo.AdminCopywritingExpertAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewExpertOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminCopywritingExpertAssignmentMapper extends BaseMapper<TCopywritingExpertAssignment> {

    List<Long> selectUserIdsForUpdate(@Param("userIds") List<Long> userIds);

    List<Long> selectActiveExpertUserIdsForUpdate(@Param("expertUserIds") List<Long> expertUserIds);

    List<Long> selectExpertIdsByAccountUserIdForUpdate(@Param("accountUserId") Long accountUserId);

    int deleteByAccountUserId(@Param("accountUserId") Long accountUserId);

    int insertAssignments(
            @Param("accountUserId") Long accountUserId,
            @Param("expertUserIds") List<Long> expertUserIds);

    int releaseClaimedTasks(
            @Param("accountUserId") Long accountUserId,
            @Param("expertUserIds") List<Long> expertUserIds);

    List<AdminCopywritingExpertAssignmentVO> selectAssignmentsByAccountUserIds(
            @Param("accountUserIds") List<Long> accountUserIds);

    List<AdminReviewExpertOptionVO> selectExpertOptions(
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    IPage<AdminReviewAccountOptionVO> selectAccountOptions(
            Page<AdminReviewAccountOptionVO> page,
            @Param("keyword") String keyword);
}
