package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import io.swagger.v3.oas.annotations.Hidden;

import java.util.List;

@Hidden
public interface AdminDigitalModelService extends IService<TAdminDigitalModel> {
    // 批量保存数字人模型数据
    boolean batchSaveModels(List<TAdminDigitalModel> modelList);

    /**
     * 分页查询数字人模型（支持多条件筛选）
     */
    IPage<TAdminDigitalModel> pageDigitalModels(Integer pageNum, Integer pageSize, String name, String gender, String status, String hospital);

    List<Long> getIdByName(List<String> modelNames);
}
