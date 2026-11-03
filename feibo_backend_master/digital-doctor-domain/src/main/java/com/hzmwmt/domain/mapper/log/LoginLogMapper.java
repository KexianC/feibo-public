package com.hzmwmt.domain.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.log.TLoginLog;

/**
 * 用户登录日志数据访问接口
 * 提供登录日志的数据库访问功能
 */
public interface LoginLogMapper extends BaseMapper<TLoginLog> {
    // 继承BaseMapper后，已包含基本的CRUD方法
    // 如需自定义SQL查询，可在此接口中声明方法并在XML映射文件中实现
}