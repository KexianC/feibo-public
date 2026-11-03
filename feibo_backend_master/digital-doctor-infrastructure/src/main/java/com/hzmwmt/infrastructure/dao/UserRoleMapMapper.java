package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.UserRoleMap;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserRoleMapMapper
 * @Date: 2025/9/3 17:22
 */
public interface UserRoleMapMapper extends BaseMapper<UserRoleMap> {

    List<Long> selectUserRoleIdsByUserId(@Param("userId") Long userId);

    Long selectExpertRoleMapIdForUpdate(@Param("userId") Long userId);

    Long selectGlobalAdminRoleMapIdForUpdate(@Param("userId") Long userId);
}
