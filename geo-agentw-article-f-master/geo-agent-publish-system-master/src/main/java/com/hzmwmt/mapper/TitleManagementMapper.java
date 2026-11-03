package com.hzmwmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.model.entity.TitleInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TitleManagementMapper extends BaseMapper<TitleInfo> {
}