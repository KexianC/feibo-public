package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RoleMapper
 * @Date: 2025/9/3 17:21
 */
public interface RoleMapper extends BaseMapper<Role> {

    List<Role> selectRoleListByRoleIds(@Param("roleIds") List<Long> roleIds);
}
