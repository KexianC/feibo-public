package com.hzmwmt.domain.mapper.packages;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.packages.TAiRechargePack;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TAiRechargePackMapper extends BaseMapper<TAiRechargePack> {
    // MyBatis-Plus 基础CRUD已通过BaseMapper提供，无需额外编写
}