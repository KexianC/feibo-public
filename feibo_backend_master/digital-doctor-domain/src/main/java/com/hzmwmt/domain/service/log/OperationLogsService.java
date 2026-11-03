package com.hzmwmt.domain.service.log;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.log.TOperationLogs;

/**
 * 操作日志服务接口
 * 提供操作日志的CRUD和分页查询功能
 */
public interface OperationLogsService extends IService<TOperationLogs> {

    // 可以根据业务需求添加额外的方法
    // 例如：按条件查询、批量操作等
    // 基本的CRUD和分页查询功能已由IService提供

    /**
     * 保存操作日志（事务保证）
     */
    boolean saveWithTransaction(TOperationLogs entity);

    /**
     * 分页查询操作日志，并批量补齐用户昵称。
     */
    Page<TOperationLogs> pageQuery(Page<TOperationLogs> page,
                                   Wrapper<TOperationLogs> queryWrapper);
}
