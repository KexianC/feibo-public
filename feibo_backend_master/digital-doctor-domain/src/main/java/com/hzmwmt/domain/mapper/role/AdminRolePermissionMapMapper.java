package com.hzmwmt.domain.mapper.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.role.TRolePermissionMap;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminRolePermissionMapMapper extends BaseMapper<TRolePermissionMap> {

    @Select("""
            SELECT permission_id
            FROM t_role_permission_map
            WHERE role_id = #{roleId}
            ORDER BY permission_id
            """)
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM t_role_permission_map WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
