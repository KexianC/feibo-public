package com.hzmwmt.domain.mapper.packages;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.packages.TAiMembershipPlan;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI会员套餐Mapper接口
 */
@Repository
public interface TAiMembershipPlanMapper extends BaseMapper<TAiMembershipPlan> {

    /**
     * 查询所有有效的会员套餐
     *
     * @return 有效会员套餐列表
     */
    List<TAiMembershipPlan> selectValidMembershipPlan();

    /**
     * 根据套餐ID查询有效的会员套餐
     *
     * @param aiMembershipPlanId 会员套餐业务ID
     * @return 会员套餐
     */
    TAiMembershipPlan selectValidMembershipPlanByAiMembershipPlanId(@Param("aiMembershipPlanId") Long aiMembershipPlanId);

    /**
     * 根据套餐ID列表查询有效的会员套餐
     *
     * @param aiMembershipPlanIds 会员套餐业务ID列表
     * @return 会员套餐列表
     */
    List<TAiMembershipPlan> selectValidMembershipPlanByAiMembershipPlanIds(@Param("aiMembershipPlanIds") List<Long> aiMembershipPlanIds);

    /**
     * 查询默认的免费会员套餐
     *
     * @return 默认免费会员套餐
     */
    TAiMembershipPlan selectDefaultAiMembershipPlan();
}