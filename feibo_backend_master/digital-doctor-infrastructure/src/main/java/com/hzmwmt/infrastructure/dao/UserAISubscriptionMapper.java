package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.UserAISubscription;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAISubscriptionMapper
 * @Date: 2025/8/26 16:00
 */
public interface UserAISubscriptionMapper extends BaseMapper<UserAISubscription> {

    List<UserAISubscription> selectUserAiSubscriptionListByUserId(@Param("userId") Long userId);
}
