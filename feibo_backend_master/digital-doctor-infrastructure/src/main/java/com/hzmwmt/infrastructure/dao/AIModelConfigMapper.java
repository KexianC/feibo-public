package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIModelConfigMapper
 * @Date: 2025/9/20 15:54
 */


public interface AIModelConfigMapper extends BaseMapper<AIModelConfig> {
    List<AIModelConfig> selectAllAiModelConfig();

    AIModelConfig selectAiModelLeafConfigByAiModelConfigId(Long aiModelConfigId);

    AIModelConfig selectAiModelRuntimeConfigByAiModelConfigId(Long aiModelConfigId);

    AIModelConfig selectAiModelHistoryConfigByAiModelConfigIdForUpdate(
            Long aiModelConfigId);
}
