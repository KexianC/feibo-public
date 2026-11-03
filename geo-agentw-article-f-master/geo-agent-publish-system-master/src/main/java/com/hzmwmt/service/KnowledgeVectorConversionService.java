package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.model.entity.KnowledgeVectorConversion;

public interface KnowledgeVectorConversionService {
    IPage<KnowledgeVectorConversion> page(Page<KnowledgeVectorConversion> page, Integer agentType, Long hospitalId,
                                          Long departmentId, Long doctorId, String fileName, Integer status);
}
