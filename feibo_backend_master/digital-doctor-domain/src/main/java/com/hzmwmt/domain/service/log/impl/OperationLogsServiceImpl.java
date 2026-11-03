package com.hzmwmt.domain.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.mapper.log.OperationLogsMapper;
import com.hzmwmt.domain.service.log.OperationLogsService;
import com.hzmwmt.domain.service.log.support.LogNicknameEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * 操作日志服务实现类
 * 实现操作日志的CRUD和分页查询功能
 */
@Service
@RequiredArgsConstructor
public class OperationLogsServiceImpl extends ServiceImpl<OperationLogsMapper, TOperationLogs> implements OperationLogsService {

    private final LogNicknameEnricher logNicknameEnricher;

    @Override
    public List<TOperationLogs> list() {
        return super.list();
    }

    @Override
    public TOperationLogs getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    public boolean save(TOperationLogs entity) {
        return super.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveWithTransaction(TOperationLogs entity) {
        return super.save(entity);
    }

    @Override
    public Page<TOperationLogs> pageQuery(Page<TOperationLogs> page,
                                          Wrapper<TOperationLogs> queryWrapper) {
        Page<TOperationLogs> result = baseMapper.selectPage(page, queryWrapper);
        logNicknameEnricher.enrichOperationLogs(result.getRecords());
        return result;
    }

    // 基本的CRUD和分页查询功能已由ServiceImpl提供
    // 可以在这里实现自定义的业务逻辑方法
}
