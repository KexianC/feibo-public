package com.hzmwmt.domain.mapper.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.role.TPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AdminPermissionMapper extends BaseMapper<TPermission> {

    @Select("""
            SELECT id, permission_id, code, name, description, reserved, create_time, update_time
            FROM t_permission
            ORDER BY code
            """)
    List<TPermission> selectAllOrdered();

    @Select({
            "<script>",
            "SELECT id, permission_id, code, name, description, reserved, create_time, update_time",
            "FROM t_permission",
            "WHERE permission_id IN",
            "<foreach collection='permissionIds' item='permissionId' open='(' separator=',' close=')'>",
            "#{permissionId}",
            "</foreach>",
            "ORDER BY permission_id",
            "</script>"
    })
    List<TPermission> selectByPermissionIds(@Param("permissionIds") Collection<Long> permissionIds);

    @Select("""
            SELECT DISTINCT permission.code
            FROM t_user_role_map user_role
            INNER JOIN t_role_permission_map role_permission
                ON role_permission.role_id = user_role.role_id
            INNER JOIN t_permission permission
                ON permission.permission_id = role_permission.permission_id
            WHERE user_role.user_id = #{userId}
            ORDER BY permission.code
            """)
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
