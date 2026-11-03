package com.hzmwmt.domain.mapper.role;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.vo.role.AdminUserRoleMapVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminUserRoleMapMapper extends BaseMapper<TUserRoleMap> {

    /**
     * 分页查询用户角色映射（关联t_user和t_role，适配TUser实体的字段）
     */
    @Select("SELECT " +
            "urm.id, " +
            "urm.user_id AS userId, " +
            "u.nick_name AS username, " +  // 关键调整：用户表的昵称字段是nick_name
            "urm.role_id AS roleId, " +
            "r.name AS roleName, " +      // 角色表的名称字段（假设t_role表是name）
            "urm.create_time AS createTime " +
            "FROM t_user_role_map urm " +
            "LEFT JOIN t_user u ON urm.user_id = u.user_id " +  // 关联用户表（TUser主键是id）
            "LEFT JOIN t_role r ON urm.role_id = r.role_id " +  // 关联角色表（假设主键是id）
            "${ew.customSqlSegment}")
    // MP条件构造器占位符
    IPage<AdminUserRoleMapVO> selectUserRoleMapPage(
            Page<AdminUserRoleMapVO> page,
            @Param(Constants.WRAPPER) Wrapper<AdminUserRoleMapVO> queryWrapper
    );

    /**
     * Locks the active super-administrator membership set while a privileged
     * revocation or account disable operation is evaluated.
     */
    @Select("""
            SELECT DISTINCT urm.user_id
            FROM t_user_role_map urm
            INNER JOIN t_user u ON u.user_id = urm.user_id
            INNER JOIN t_role r ON r.role_id = urm.role_id
            WHERE u.status = 0
              AND UPPER(TRIM(r.name)) IN ('SUPER_ADMIN', 'SUPERADMIN')
            FOR UPDATE
            """)
    List<Long> selectActiveSuperAdminUserIdsForUpdate();
}
