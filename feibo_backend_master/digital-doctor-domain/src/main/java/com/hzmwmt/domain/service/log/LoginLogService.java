package com.hzmwmt.domain.service.log;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.log.TLoginLog;

/**
 * 用户登录日志服务接口
 * 提供登录日志的CRUD和分页查询功能
 */
public interface LoginLogService extends IService<TLoginLog> {

    /**
     * 根据条件分页查询登录日志
     * 扩展分页查询功能，支持更复杂的条件查询
     *
     * @param pageNum     页码
     * @param pageSize    每页条数
     * @param loginUser   登录用户名
     * @param loginIp     登录IP
     * @param loginStatus 登录状态
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @return 分页结果
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<TLoginLog> pageQuery(
            Integer pageNum,
            Integer pageSize,
            String loginUser,
            String loginIp,
            String loginStatus,
            String startTime,
            String endTime);
}