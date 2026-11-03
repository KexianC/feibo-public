package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIFeature;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIFeatureMapper
 * @Date: 2025/8/26 15:57
 */
public interface AIFeatureMapper extends BaseMapper<AIFeature> {

    List<AIFeature> selectAiFeatureListByAiFeatureIds(@Param("aiFeatureIds") List<Long> aiFeatureIds);
}