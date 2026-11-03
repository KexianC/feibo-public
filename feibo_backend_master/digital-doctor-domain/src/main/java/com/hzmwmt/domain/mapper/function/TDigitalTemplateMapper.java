package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TDigitalTemplate;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden
public interface TDigitalTemplateMapper extends BaseMapper<TDigitalTemplate> {
    // 继承BaseMapper，自动拥有CRUD方法
}
