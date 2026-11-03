package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.CopywritingOrganizationAdminAssignment;
import org.apache.ibatis.annotations.Param;

public interface CopywritingOrganizationAdminAssignmentMapper
        extends BaseMapper<CopywritingOrganizationAdminAssignment> {

    Long selectActiveAdminUserIdForUpdate(@Param("adminUserId") Long adminUserId);

    Long selectOrganizationAdminRoleMapIdForUpdate(@Param("adminUserId") Long adminUserId);

    Long selectAssignmentIdForUpdate(
            @Param("copywritingId") Long copywritingId,
            @Param("adminUserId") Long adminUserId);
}
