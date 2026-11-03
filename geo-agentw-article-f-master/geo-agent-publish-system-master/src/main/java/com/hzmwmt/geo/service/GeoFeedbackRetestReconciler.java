package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.geo.mapper.GeoFeedbackSuggestionMapper;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Repairs a task callback interrupted by a process restart or transient database error. */
@Slf4j
@Service
public class GeoFeedbackRetestReconciler {
    @Resource private GeoFeedbackSuggestionMapper suggestions;
    @Resource private GeoFeedbackWorkflowService workflow;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void reconcile() {
        for (GeoFeedbackSuggestion suggestion : suggestions.selectList(new LambdaQueryWrapper<GeoFeedbackSuggestion>()
                .isNotNull(GeoFeedbackSuggestion::getActiveRetestBatchId)
                .in(GeoFeedbackSuggestion::getRetestStatus, 1, 2)
                .orderByAsc(GeoFeedbackSuggestion::getUpdatedTime).last("LIMIT 100"))) {
            try {
                workflow.refreshRetestState(suggestion.getId());
            } catch (Exception error) {
                log.warn("GEO retest reconciliation failed: suggestionId={}, exceptionType={}",
                        suggestion.getId(), error.getClass().getSimpleName());
            }
        }
    }
}
