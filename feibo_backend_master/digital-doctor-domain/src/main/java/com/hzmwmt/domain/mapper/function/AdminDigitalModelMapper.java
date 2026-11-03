package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminDigitalModelMapper extends BaseMapper<TAdminDigitalModel> {
    // 继承BaseMapper，自动支持CRUD
}
