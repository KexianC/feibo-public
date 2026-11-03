package com.hzmwmt.domain.trade.model.aggregate;

import com.hzmwmt.domain.trade.model.entity.AIFeatureEntity;
import com.hzmwmt.domain.trade.model.entity.AIMembershipPlanEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMembershipPlanAggregate
 * @Date: 2025/8/27 9:31
 */
@Builder
@Data
public class AIMembershipPlanAggregate {
    private AIMembershipPlanEntity aiMembershipPlanEntity;
    private List<AIFeatureEntity> aiFeatureEntities;
}
