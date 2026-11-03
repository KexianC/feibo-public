package com.hzmwmt.domain.service.order;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.vo.role.AdminUserRoleMapVO;

public interface AdminUserRoleMapService extends IService<TUserRoleMap> {

    // 分页查询VO（含用户名和角色名）
    IPage<AdminUserRoleMapVO> selectUserRoleMapVOPage(Page<AdminUserRoleMapVO> page, Wrapper<AdminUserRoleMapVO> queryWrapper);

    /**
     * Enforces the invariant that at least one active super administrator
     * remains before a user account is disabled.
     */
    void assertCanDisableUser(Long userId);
}
