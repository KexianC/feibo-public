package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import com.hzmwmt.domain.mapper.function.TCopywritingCategoryMapper;
import com.hzmwmt.domain.service.function.AdminCopywritingCategoryService;
import org.springframework.stereotype.Service;

@Service
public class AdminCopywritingCategoryServiceImpl extends ServiceImpl<TCopywritingCategoryMapper, TCopywritingCategory>
        implements AdminCopywritingCategoryService {

    // 继承 ServiceImpl 后，自动注入 Mapper，可直接使用 baseMapper 操作数据库

    @Override
    public IPage<TCopywritingCategory> pageCategories(Integer pageNum, Integer pageSize) {
        // 构建分页对象，使用 MP 分页查询
        Page<TCopywritingCategory> page = new Page<>(pageNum, pageSize);
        // 如需条件查询，可在第二个参数添加 QueryWrapper
        return baseMapper.selectPage(page, null);
    }
}