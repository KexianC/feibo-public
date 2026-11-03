package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.Copywriting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingMapper
 * @Date: 2025/9/3 17:19
 */
public interface CopywritingMapper extends BaseMapper<Copywriting> {

    int updateStatus2Complete(@Param("copywritingId") Long copywritingId);

    int updateStatus2Failure(@Param("copywritingId") Long copywritingId);

    List<Copywriting> selectCopyWritingListByCopywritingIds(@Param("copywritingIds") List<Long> copywritingIds);

    List<Copywriting> selectVisibleCopywritingListByIds(
            @Param("copywritingIds") List<Long> copywritingIds,
            @Param("actorUserId") Long actorUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization);

    List<Long> selectCopywritingIdsPageByScope(
            @Param("actorUserId") Long actorUserId,
            @Param("allAccess") boolean allAccess,
            @Param("offset") long offset,
            @Param("size") Integer size,
            @Param("status") Integer status);

    long countCopywritingsByScope(
            @Param("actorUserId") Long actorUserId,
            @Param("allAccess") boolean allAccess,
            @Param("status") Integer status);

    List<Long> selectCopywritingIdsPageByUserIdAndStatus(@Param("userId") Long userId, @Param("offset") long offset, @Param("size") Integer size, @Param("status") Integer status);

    List<Long> selectPublishedCopywritingIdsByUserId(Long userId);

}
