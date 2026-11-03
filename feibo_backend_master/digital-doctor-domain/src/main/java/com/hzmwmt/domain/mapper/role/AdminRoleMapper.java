package com.hzmwmt.domain.mapper.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.role.TRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminRoleMapper extends BaseMapper<TRole> {

    @Select("""
            SELECT id, role_id, name, description, create_time, update_time
            FROM t_role
            WHERE id = #{databaseId}
            FOR UPDATE
            """)
    TRole selectByDatabaseIdForUpdate(@Param("databaseId") Long databaseId);

    /**
     * Stable serialization anchor for every operation that can change the
     * active super-administrator set.
     */
    @Select("""
            SELECT role_id
            FROM t_role
            WHERE UPPER(TRIM(name)) IN ('SUPER_ADMIN', 'SUPERADMIN')
            ORDER BY role_id
            FOR UPDATE
            """)
    List<Long> selectSuperAdminRoleIdsForUpdate();
}
