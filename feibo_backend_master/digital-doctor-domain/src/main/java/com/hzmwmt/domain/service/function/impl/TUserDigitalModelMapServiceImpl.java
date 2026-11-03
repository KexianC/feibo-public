package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import com.hzmwmt.domain.mapper.function.TUserDigitalModelMapMapper;
import com.hzmwmt.domain.service.function.TUserDigitalModelMapService;
import com.hzmwmt.types.common.CursorPageResponseData;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户数字人模型映射服务实现类
 */
@Service
public class TUserDigitalModelMapServiceImpl extends ServiceImpl<TUserDigitalModelMapMapper, TUserDigitalModelMap> implements TUserDigitalModelMapService {

    @Override
    public CursorPageResponseData<TUserDigitalModelMap> queryUserDigitalModels(Long userId, Long lastCursorId, Integer size) {
        // 构建查询条件
        LambdaQueryWrapper<TUserDigitalModelMap> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TUserDigitalModelMap::getUserId, userId);

        // 如果有游标ID，使用游标分页
        if (lastCursorId != null && lastCursorId > 0) {
            queryWrapper.lt(TUserDigitalModelMap::getId, lastCursorId);
        }

        // 按ID倒序，确保游标分页一致性
        queryWrapper.orderByDesc(TUserDigitalModelMap::getId);

        // 限制查询数量
        if (size == null || size <= 0) {
            size = 10; // 默认10条
        }
        queryWrapper.last("LIMIT " + size + 1); // 多查询1条用于判断是否有下一页

        // 执行查询
        List<TUserDigitalModelMap> mapList = baseMapper.selectList(queryWrapper);

        // 构建响应
        CursorPageResponseData<TUserDigitalModelMap> response = new CursorPageResponseData<>();
        response.setData(mapList.isEmpty() ? null : mapList.subList(0, Math.min(mapList.size(), size)));

        // 设置下一页游标
        if (mapList.size() > size) {
            response.setHasNext(true);
            response.setNextCursorId(mapList.get(size).getId());
        } else {
            response.setHasNext(false);
        }

        return response;
    }
}