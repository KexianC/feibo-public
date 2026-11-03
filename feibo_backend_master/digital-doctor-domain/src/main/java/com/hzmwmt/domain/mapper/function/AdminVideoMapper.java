package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TVideo;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // 隐藏 Mapper，不暴露到 Swagger 文档
public interface AdminVideoMapper extends BaseMapper<TVideo> {
    // 继承 BaseMapper，自动实现 CRUD
}