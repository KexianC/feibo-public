package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // Swagger 隐藏 Mapper 接口（无需在文档中展示）
public interface TCopywritingCategoryMapper extends BaseMapper<TCopywritingCategory> {
    // 继承 BaseMapper 后，无需手动编写 CRUD 方法，MP 自动实现
}