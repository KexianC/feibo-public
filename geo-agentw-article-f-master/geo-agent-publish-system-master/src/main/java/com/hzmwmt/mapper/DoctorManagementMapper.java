package com.hzmwmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.model.entity.DoctorInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DoctorManagementMapper extends BaseMapper<DoctorInfo> {

}
