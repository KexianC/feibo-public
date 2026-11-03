package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数字人模型映射表Mapper
 * 继承BaseMapper，自动支持CRUD操作
 */
@Mapper
public interface TUserDigitalModelMapMapper extends BaseMapper<TUserDigitalModelMap> {
    // 继承BaseMapper后，MyBatis-Plus自动提供基本CRUD方法
}