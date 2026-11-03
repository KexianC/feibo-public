package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TDigitalMusic;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // 不在Swagger文档中显示
public interface AdminDigitalMusicMapper extends BaseMapper<TDigitalMusic> {
    // 继承BaseMapper，自动实现TDigitalMusic的CRUD方法（依赖MP）
}