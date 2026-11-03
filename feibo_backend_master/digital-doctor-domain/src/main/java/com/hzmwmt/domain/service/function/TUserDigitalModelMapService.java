package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import com.hzmwmt.types.common.CursorPageResponseData;

/**
 * 用户数字人模型映射服务接口
 */
public interface TUserDigitalModelMapService extends IService<TUserDigitalModelMap> {

    /**
     * 根据用户ID查询绑定的数字人模型
     *
     * @param userId       用户ID
     * @param lastCursorId 游标ID
     * @param size         每页数量
     * @return 数字人模型映射列表（包含data、hasNext和nextCursorId字段）
     */
    CursorPageResponseData<TUserDigitalModelMap> queryUserDigitalModels(Long userId, Long lastCursorId, Integer size);
}