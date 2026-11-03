package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.UserAIResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAIResourceMapper
 * @Date: 2025/8/26 16:01
 */
public interface UserAIResourceMapper extends BaseMapper<UserAIResource> {

    Long selectAdvancedAiModelResourceIdByUserId(Long userId);

    List<UserAIResource> selectUserAiResourceListByUserId(Long userId);

    Long selectDigitalResourceIdByUserId(Long userId);

    Long selectUserAiResourceIdByUserIdAndFeatureTemplateId(Long userId, Long aiFeatureTemplateId);

    List<UserAIResource> selectByUserIdAndFeatureTemplateIds(@Param("userId") Long userId, @Param("featureTemplateIds") List<Long> featureTemplateIds);
}
