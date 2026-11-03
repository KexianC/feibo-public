package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.mapper.function.AdminDigitalModelMapper;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminDigitalModelServiceImpl extends ServiceImpl<AdminDigitalModelMapper, TAdminDigitalModel> implements AdminDigitalModelService {

    @Resource
    private AdminDigitalModelMapper adminDigitalModelMapper;

    @Override
    public boolean batchSaveModels(List<TAdminDigitalModel> modelList) {
        return saveBatch(modelList); // 批量保存
    }

    @Override
    public IPage<TAdminDigitalModel> pageDigitalModels(Integer pageNum, Integer pageSize, String name, String gender, String status, String hospital) {
        Page<TAdminDigitalModel> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TAdminDigitalModel> wrapper = new QueryWrapper<>();
        // 姓名模糊查询
        if (name != null && !name.isEmpty()) {
            wrapper.like("model_name", name);
        }
        // 性别精确匹配
        if (gender != null && !gender.isEmpty()) {
            wrapper.eq("gender", gender);
        }
        // 状态精确匹配
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        // 医院精确匹配
        if (hospital != null && !hospital.isEmpty()) {
            wrapper.eq("hospital", hospital);
        }
        // 与模型排序设置一致，相同排序值时按 ID 稳定分页。
        wrapper.orderByAsc("sort_num").orderByDesc("id");
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<Long> getIdByName(List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<TAdminDigitalModel> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id").in("model_name", modelNames);
        return baseMapper.selectList(queryWrapper).stream().map(model -> Long.parseLong(model.getId())).collect(Collectors.toList());
    }
}
