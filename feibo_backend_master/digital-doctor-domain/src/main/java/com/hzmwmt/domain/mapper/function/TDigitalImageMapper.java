package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TDigitalImage;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // 不在Swagger文档显示
public interface TDigitalImageMapper extends BaseMapper<TDigitalImage> {
    // 继承MP的BaseMapper，自动实现CRUD
    // 此mapper统一管理背景图片(type=0)和插图(type=1)
}