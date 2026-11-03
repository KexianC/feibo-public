package com.hzmwmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.model.entity.HospitalInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HospitalManagementMapper extends BaseMapper<HospitalInfo> {
}