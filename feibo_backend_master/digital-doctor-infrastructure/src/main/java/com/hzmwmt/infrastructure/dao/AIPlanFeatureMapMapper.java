package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIPlanFeatureMap;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIPlanFeatureMapMapper
 * @Date: 2025/8/27 13:13
 */
public interface AIPlanFeatureMapMapper extends BaseMapper<AIPlanFeatureMap> {

    List<AIPlanFeatureMap> selectAiPlanFeatureMapListByMembershipPlanIds(@Param("membershipPlanIds") List<Long> membershipPlanIds);

    List<Long> selectAiFeatureIdsByMembershipPlanId(@Param("aiMembershipPlanId") Long aiMembershipPlanId);

    List<AIPlanFeatureMap> selectByAiMembershipPlanId(Long aiMembershipPlanId);
}
