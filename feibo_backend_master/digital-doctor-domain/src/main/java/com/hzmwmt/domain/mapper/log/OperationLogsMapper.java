package com.hzmwmt.domain.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志Mapper接口
 * 提供操作日志的数据库访问功能
 */
@Mapper
public interface OperationLogsMapper extends BaseMapper<TOperationLogs> {

    // 基本的数据库操作已由BaseMapper提供
    // 可以在这里添加自定义的查询方法
}