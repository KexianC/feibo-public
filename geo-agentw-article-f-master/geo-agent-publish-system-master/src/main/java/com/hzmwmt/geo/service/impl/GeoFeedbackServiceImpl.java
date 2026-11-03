package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.mapper.GeoFeedbackSuggestionMapper;
import com.hzmwmt.geo.model.dto.GeoFeedbackGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.service.GeoFeedbackService;
import com.hzmwmt.geo.service.GeoOptimizationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoFeedbackServiceImpl implements GeoFeedbackService {

    @Resource
    private GeoFeedbackSuggestionMapper feedbackSuggestionMapper;

    @Resource
    private GeoOptimizationService geoOptimizationService;

    @Override
    public List<GeoFeedbackSuggestion> generateSuggestions(GeoFeedbackGenerateRequest request) {
        return geoOptimizationService.generateSuggestions(request);
    }

    @Override
    public IPage<GeoFeedbackSuggestion> page(Page<GeoFeedbackSuggestion> page, Long queryId, Long articleId, Integer status) {
        LambdaQueryWrapper<GeoFeedbackSuggestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryId != null, GeoFeedbackSuggestion::getQueryId, queryId);
        wrapper.eq(articleId != null, GeoFeedbackSuggestion::getArticleId, articleId);
        wrapper.eq(status != null, GeoFeedbackSuggestion::getStatus, status);
        wrapper.orderByDesc(GeoFeedbackSuggestion::getCreatedTime);
        return feedbackSuggestionMapper.selectPage(page, wrapper);
    }
}
