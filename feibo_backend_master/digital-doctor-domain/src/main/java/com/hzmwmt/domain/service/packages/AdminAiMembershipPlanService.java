package com.hzmwmt.domain.service.packages;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.packages.TAiMembershipPlan;

/**
 * 后台AI会员套餐管理Service接口
 */
public interface AdminAiMembershipPlanService extends IService<TAiMembershipPlan> {

    /**
     * 分页+条件查询AI会员套餐
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param planName 套餐名称（模糊匹配）
     * @return 分页结果
     */
    IPage<TAiMembershipPlan> pageWithCondition(Integer pageNum, Integer pageSize, String planName);
}