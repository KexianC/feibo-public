package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TMusic;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden // 不在Swagger文档中显示
public interface AdminMusicMapper extends BaseMapper<TMusic> {
    // 继承BaseMapper，自动实现TMusic的CRUD方法（依赖MP）
}
