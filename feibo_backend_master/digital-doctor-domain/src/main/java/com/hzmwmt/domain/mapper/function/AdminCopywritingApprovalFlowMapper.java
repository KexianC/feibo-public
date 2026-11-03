package com.hzmwmt.domain.mapper.function;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Hidden
public interface AdminCopywritingApprovalFlowMapper extends BaseMapper<TCopywritingApprovalFlow> {
    // 继承 BaseMapper，自动实现 CRUD
}