package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.CopywritingExpertAssignment;
import org.apache.ibatis.annotations.Param;

public interface CopywritingExpertAssignmentMapper extends BaseMapper<CopywritingExpertAssignment> {

    Long selectActiveExpertUserIdForUpdate(@Param("expertUserId") Long expertUserId);

    Long selectAssignmentIdForUpdate(
            @Param("copywritingId") Long copywritingId,
            @Param("expertUserId") Long expertUserId);
}
