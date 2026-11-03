package com.hzmwmt.domain.service.packages.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.hzmwmt.domain.entity.packages.TAiMembershipPlan;
import com.hzmwmt.domain.mapper.packages.TAiMembershipPlanMapper;
import com.hzmwmt.domain.service.packages.AdminAiMembershipPlanService;
import org.springframework.stereotype.Service;

/**
 * 后台AI会员套餐管理Service实现类
 */
@Service
public class AdminAiMembershipPlanServiceImpl extends ServiceImpl<TAiMembershipPlanMapper, TAiMembershipPlan> implements AdminAiMembershipPlanService {

    /**
     * 分页+条件查询AI会员套餐
     */
    @Override
    public IPage<TAiMembershipPlan> pageWithCondition(Integer pageNum, Integer pageSize, String planName) {
        // 初始化分页对象
        IPage<TAiMembershipPlan> page = new Page<>(pageNum, pageSize);

        // 构建查询条件
        QueryWrapper<TAiMembershipPlan> queryWrapper = new QueryWrapper<>();

        // 动态添加条件（非空才添加）
        if (StrUtil.isNotBlank(planName)) {
            // 套餐名称模糊匹配（忽略大小写）
            queryWrapper.like("name", planName);
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc("create_time");

        // 执行分页查询
        return baseMapper.selectPage(page, queryWrapper);
    }
}