package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIMembershipPlan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMembershipPlanMapper
 * @Date: 2025/8/26 15:17
 */
public interface AIMembershipPlanMapper extends BaseMapper<AIMembershipPlan> {

    List<AIMembershipPlan> selectValidMembershipPlan();

    AIMembershipPlan selectValidMembershipPlanByAiMembershipPlanId(@Param("aiMembershipPlanId") Long aiMembershipPlanId);

    List<AIMembershipPlan> selectValidMembershipPlanByAiMembershipPlanIds(@Param("aiMembershipPlanIds") List<Long> aiMembershipPlanIds);

    AIMembershipPlan selectDefaultAiMembershipPlan();
}