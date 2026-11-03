package com.hzmwmt.domain.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import com.hzmwmt.domain.mapper.user.AdminDigitalWorkMapper;
import com.hzmwmt.domain.mapper.user.DigitalWorkComposeAuditMapper;
import com.hzmwmt.domain.service.user.AdminDigitalWorkService;
import com.hzmwmt.types.annotations.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数字作品 Service 实现类
 * 继承 ServiceImpl，复用 MP 通用业务逻辑
 */
@Service
public class AdminDigitalWorkServiceImpl extends ServiceImpl<AdminDigitalWorkMapper, TDigitalWork> implements AdminDigitalWorkService {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;


    @Autowired
    private AdminDigitalWorkMapper digitalWorkMapper;
    @Autowired
    private DigitalWorkComposeAuditMapper digitalWorkComposeAuditMapper;


    @Override
    // @OperationLog(operationType = "查询", description = "分页查询数字作品列表", recordParams = true)
    public IPage<TDigitalWork> pageQuery(Integer pageNum, Integer pageSize, String keyword) {
        // 1. 初始化分页参数
        Page<TDigitalWork> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
        LambdaQueryWrapper<TDigitalWork> queryWrapper = new LambdaQueryWrapper<>();

        // 关键字：标题模糊查询
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(TDigitalWork::getTitle, keyword);
        }

        // 排序：按创建时间降序
        queryWrapper.orderByDesc(TDigitalWork::getCreateTime);

        // 3. 执行分页查询（MP 封装的 page 方法）
        return this.page(page, queryWrapper);
    }
    @Override
    public IPage<Map<String, Object>> pageAuditQuery(Integer pageNum, Integer pageSize, String keyword) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            normalizedKeyword = normalizedKeyword.substring(0, MAX_KEYWORD_LENGTH);
        }
        Page<Map<String, Object>> page = new Page<>(safePageNum, safePageSize);
        return digitalWorkComposeAuditMapper.selectAuditPage(page, normalizedKeyword);
    }

    @Override
    public Map<String, Object> getAuditStatistics(LocalDate startDate, LocalDate endDate) {
        return digitalWorkComposeAuditMapper.selectAuditStatistics(startDate, endDate);
    }


    /**
     * 单个删除：调用 MP 的 removeById 方法
     */
    @Override
    @OperationLog(operationType = "删除", description = "删除数字作品", recordParams = true)
    public boolean deleteById(Integer id) {
        // 可选：删除前校验（如判断记录是否存在）
        if (id == null) {
            return false; // ID为空，删除失败
        }
        TDigitalWork work = getById(id);
        if (work != null && work.getQuotaBizKey() != null) {
            if (work.getStatus() == null) return false;
            // Keep quota/idempotency evidence, including after a privileged deletion.
            return lambdaUpdate().eq(TDigitalWork::getId,id).set(TDigitalWork::getQuotaHidden,true).update();
        }
        return this.removeById(id);
    }

    /**
     * 批量删除：调用 MP 的 removeByIds 方法
     */
    @Override
    @OperationLog(operationType = "删除", description = "批量删除数字作品", recordParams = true)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Integer> ids) {
        // 可选：删除前校验（如集合为空或包含null）
        if (ids == null || ids.isEmpty() || ids.contains(null)) {
            return false; // 无效ID列表，删除失败
        }
        List<TDigitalWork> works = listByIds(ids);
        if (works.size() != ids.stream().distinct().count()
                || works.stream().anyMatch(w -> w.getQuotaBizKey() != null && w.getStatus() == null)) return false;
        boolean all = true;
        for (Integer id : ids) all &= deleteById(id);
        return all;
    }

    @Override
    public List<Map<String, Integer>> getTopUser(Integer topNum, LocalDate startDate, LocalDate endDate) {
        if (topNum == null || topNum <= 0 || topNum > 50) {
            topNum = 10; // 默认取10名
        }
        return digitalWorkMapper.getTopUser(topNum, startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay());
    }

    /**
     * 保存前根据时长计算bill
     * 计算规则：1分钟50元
     */
    @Override
    @OperationLog(operationType = "新增", description = "保存数字作品", recordParams = true)
    public boolean save(TDigitalWork entity) {
        // 根据时长计算bill
        calculateBill(entity);
        return super.save(entity);
    }

    /**
     * 更新前根据时长计算bill
     */
    @Override
    @OperationLog(operationType = "更新", description = "更新数字作品", recordParams = true)
    public boolean updateById(TDigitalWork entity) {
        // 根据时长计算bill
        calculateBill(entity);
        return super.updateById(entity);
    }

    /**
     * 根据时长计算bill的核心逻辑
     * 计算规则：1分钟以下按1分钟计算，1分钟以上按实际时间计算
     * 1分钟 = 50元
     *
     * @param entity 数字作品实体
     */
    private void calculateBill(TDigitalWork entity) {
        if (entity.getDuration() != null && entity.getDuration() > 0) {
            // 计算时长（分钟）
            double minutes;
            if (entity.getDuration() <= 60) {
                // 1分钟以下按1分钟计算
                minutes = 1.0;
            } else {
                // 1分钟以上按实际时间计算（保留小数）
                minutes = entity.getDuration() / 60.0;
            }
            // 计算费用：1分钟50元
            entity.setBill(minutes * 50.0);
        }
    }
}
