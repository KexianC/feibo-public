package com.hzmwmt.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.dto.AdminAiModelCreateDTO;
import com.hzmwmt.domain.dto.AdminAiModelUpdateDTO;
import com.hzmwmt.domain.entity.user.TAiModelConfig;
import com.hzmwmt.domain.vo.AdminAiModelVO;
import com.hzmwmt.domain.vo.AdminModelConfigVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AdminModelConfigService extends IService<TAiModelConfig> {
    /**
     * 根据name的到id
     */
    List<Long> getIdByName(String name);

    /**
     * 得到所有ai模型的id 名字
     */
    List<AdminModelConfigVO> getAdminModelConfigs();

    List<AdminAiModelVO> listManagedModels();

    AdminAiModelVO createManagedModel(AdminAiModelCreateDTO request);

    AdminAiModelVO updateManagedModel(Long aiModelConfigId, AdminAiModelUpdateDTO request);

    List<AdminAiModelVO> updateProviderOrder(Long providerId, Integer order);

    AdminAiModelVO updateVisibility(Long aiModelConfigId, Boolean visible);

    AdminAiModelVO updateApiKey(Long aiModelConfigId, String apiKey);

    void deleteManagedModel(Long aiModelConfigId);


}
