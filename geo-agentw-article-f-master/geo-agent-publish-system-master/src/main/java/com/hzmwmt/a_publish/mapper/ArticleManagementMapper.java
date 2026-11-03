package com.hzmwmt.a_publish.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleManagementMapper extends BaseMapper<ArticleInfo> {
}