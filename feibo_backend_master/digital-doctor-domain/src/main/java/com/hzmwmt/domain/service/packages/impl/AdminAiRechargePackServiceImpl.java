package com.hzmwmt.domain.service.packages.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.packages.TAiRechargePack;
import com.hzmwmt.domain.mapper.packages.TAiRechargePackMapper;
import com.hzmwmt.domain.service.packages.AdminAiRechargePackService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class AdminAiRechargePackServiceImpl extends ServiceImpl<TAiRechargePackMapper, TAiRechargePack> implements AdminAiRechargePackService {

    @Override
    public IPage<TAiRechargePack> pageWithCondition(Integer pageNum, Integer pageSize, String name) {
        return pageWithCondition(pageNum, pageSize, name, null, null, null);
    }

    @Override
    public IPage<TAiRechargePack> pageWithCondition(Integer pageNum, Integer pageSize, String name, Integer status, BigDecimal minPrice, BigDecimal maxPrice) {
        IPage<TAiRechargePack> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TAiRechargePack> queryWrapper = new QueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }
        if (status != null) queryWrapper.eq("is_active", status);
        if (minPrice != null) queryWrapper.ge("price", minPrice);
        if (maxPrice != null) queryWrapper.le("price", maxPrice);
        queryWrapper.orderByDesc("create_time", "id");
        return baseMapper.selectPage(page, queryWrapper);
    }
}
