package com.hzmwmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.model.entity.DepartmentInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentManagementMapper extends BaseMapper<DepartmentInfo> {
}