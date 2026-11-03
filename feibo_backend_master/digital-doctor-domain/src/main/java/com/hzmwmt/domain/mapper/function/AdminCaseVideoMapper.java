package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TCaseVideo;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // 隐藏 Mapper 接口，不暴露到 Swagger 文档
public interface AdminCaseVideoMapper extends BaseMapper<TCaseVideo> {
}
