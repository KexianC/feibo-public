package com.hzmwmt.domain.ai.adapter.port;

import com.hzmwmt.domain.ai.model.aggregate.ApplicationAIAggregate;
import com.hzmwmt.domain.ai.model.entity.AIContentReviewResultEntity;
import reactor.core.Disposable;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAIPort
 * @Date: 2025/8/26 17:46
 */
public interface IAIPort {
    Disposable symptomAnalysis(ApplicationAIAggregate applicationAiAggregate);

    Disposable symptomAnalysisByTencentAi(ApplicationAIAggregate applicationAiAggregate);

    /**
     * 获取AI推荐搜索
     * @return
     */
    List<String> getAiRecommendSearch();

    Boolean verifyAiQuota(Long userId);

    /**
     * AI 内容审核：敏感词、错别字、科学性错误
     *
     * @param content 待审核内容
     * @return 审核结果
     */
    AIContentReviewResultEntity reviewContent(String content);
}