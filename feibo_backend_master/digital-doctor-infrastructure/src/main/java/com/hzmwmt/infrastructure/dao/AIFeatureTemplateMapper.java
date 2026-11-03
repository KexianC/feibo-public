package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIFeatureTemplate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIFeatureTemplateMapper
 * @Date: 2025/8/27 13:11
 */
public interface AIFeatureTemplateMapper extends BaseMapper<AIFeatureTemplate> {

    List<AIFeatureTemplate> selectAIFeatureTemplateListByAIFeatureTemplateIds(@Param("templateIds") List<Long> templateIds);
}
