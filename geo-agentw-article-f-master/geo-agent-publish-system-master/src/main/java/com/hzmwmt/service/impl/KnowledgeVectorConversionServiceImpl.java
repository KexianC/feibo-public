package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.mapper.KnowledgeVectorConversionMapper;
import com.hzmwmt.model.entity.KnowledgeVectorConversion;
import com.hzmwmt.service.KnowledgeVectorConversionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeVectorConversionServiceImpl implements KnowledgeVectorConversionService {
    @Resource
    KnowledgeVectorConversionMapper vectorConversionMapper;

    @Override
    public IPage<KnowledgeVectorConversion> page(Page<KnowledgeVectorConversion> page, Integer agentType, Long hospitalId,
                                                 Long departmentId, Long doctorId, String fileName, Integer status) {
        LambdaQueryWrapper<KnowledgeVectorConversion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(fileName != null && !fileName.isEmpty(), KnowledgeVectorConversion::getFileName, fileName);
        queryWrapper.eq(agentType != null, KnowledgeVectorConversion::getAgentType, agentType);
        queryWrapper.eq(hospitalId != null, KnowledgeVectorConversion::getHospitalId, hospitalId);
        queryWrapper.eq(departmentId != null, KnowledgeVectorConversion::getDepartmentId, departmentId);
        queryWrapper.eq(doctorId != null, KnowledgeVectorConversion::getDoctorId, doctorId);
        queryWrapper.eq(status != null, KnowledgeVectorConversion::getStatus, status);
        return vectorConversionMapper.selectPage(page,queryWrapper);
    }
}
