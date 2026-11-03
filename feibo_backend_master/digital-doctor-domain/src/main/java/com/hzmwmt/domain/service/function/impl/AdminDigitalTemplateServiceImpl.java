package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TDigitalTemplate;
import com.hzmwmt.domain.mapper.function.TDigitalTemplateMapper;
import com.hzmwmt.domain.service.function.AdminDigitalTemplateService;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

@Service
public class AdminDigitalTemplateServiceImpl extends ServiceImpl<TDigitalTemplateMapper, TDigitalTemplate> implements AdminDigitalTemplateService {

    @Override
    public List<TDigitalTemplate> list() {
        return super.list();
    }

    @Override
    public TDigitalTemplate getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    public boolean save(TDigitalTemplate entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(TDigitalTemplate entity) {
        return super.updateById(entity);
    }

    @Override
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
    // 可重写/扩展业务逻辑
}
