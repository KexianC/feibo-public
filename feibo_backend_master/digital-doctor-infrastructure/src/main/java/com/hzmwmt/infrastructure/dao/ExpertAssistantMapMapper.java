package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.ExpertAssistantMap;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ExpertAssistantMapMapper
 * @Date: 2025/9/6 16:36
 */
public interface ExpertAssistantMapMapper extends BaseMapper<ExpertAssistantMap> {
    List<Long> selectAssistantIdsByExpertId(@Param("expertUserId") Long expertUserId);

    List<Long> selectExpertIdsByAssistantId(@Param("assistantUserId") Long assistantUserId);

    List<Long> selectExpertIdsPageByAssistantId(@Param("assistantUserId") Long assistantUserId, @Param("offset") Long offset, @Param("size") Integer size);

    List<Long> selectAllExpertIdsByAssistantId(@Param("assistantUserId") Long assistantUserId);

    List<Long> selectAssistantIdsPageByExpertId(@Param("expertUserId") Long userId, @Param("offset") Long offset, @Param("size") Integer size);
}
