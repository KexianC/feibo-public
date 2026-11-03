package com.hzmwmt.trigger.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.model.entity.AIContentReviewResultEntity;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.ICopyWritingApprovalService;
import com.hzmwmt.infrastructure.dao.CopywritingApprovalFlowMapper;
import com.hzmwmt.infrastructure.dao.CopywritingMapper;
import com.hzmwmt.infrastructure.dao.po.Copywriting;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalFlow;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches AI review immediately after the author confirms the copywriting.
 */
@Slf4j
@Service
public class CopywritingAiApprovalJob {

    private static final Long AI_OPERATOR_ID = 0L;
    private static final long MAX_RETRY_DELAY_SECONDS = 60L;

    private final Set<Long> queuedCopywritingIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "copywriting-ai-retry");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private CopywritingApprovalFlowMapper approvalFlowMapper;
    @Resource
    private CopywritingMapper copywritingMapper;
    @Resource
    private IAIPort aiPort;
    @Resource
    private ICopyWritingApprovalService copyWritingApprovalService;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingApprovals() {
        List<CopywritingApprovalFlow> pendingFlows = approvalFlowMapper.selectList(
                new LambdaQueryWrapper<CopywritingApprovalFlow>()
                        .eq(CopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.AI_PENDING.getCode())
                        .orderByAsc(CopywritingApprovalFlow::getCreateTime));
        for (CopywritingApprovalFlow flow : pendingFlows) {
            if (flow != null && flow.getCopywritingId() != null) {
                triggerApprovalAsync(flow.getCopywritingId());
            }
        }
    }

    public void triggerApprovalAsync(Long copywritingId) {
        if (copywritingId == null) {
            throw new IllegalArgumentException("copywritingId must not be null");
        }
        if (!queuedCopywritingIds.add(copywritingId)) {
            return;
        }
        scheduleAttempt(copywritingId, 1, 0);
    }

    private void scheduleAttempt(Long copywritingId, int attempt, long delaySeconds) {
        Runnable dispatch = () -> {
            try {
                threadPoolExecutor.execute(() -> executeAttempt(copywritingId, attempt));
            } catch (RuntimeException dispatchException) {
                handleAttemptFailure(copywritingId, attempt, dispatchException);
            }
        };

        try {
            if (delaySeconds <= 0) {
                dispatch.run();
            } else {
                retryScheduler.schedule(dispatch, delaySeconds, TimeUnit.SECONDS);
            }
        } catch (RuntimeException schedulingException) {
            queuedCopywritingIds.remove(copywritingId);
            log.error("Unable to schedule AI review, copywritingId: {}, exceptionType: {}",
                    copywritingId, schedulingException.getClass().getSimpleName());
        }
    }

    private void executeAttempt(Long copywritingId, int attempt) {
        try {
            triggerApproval(copywritingId);
            queuedCopywritingIds.remove(copywritingId);
        } catch (Exception exception) {
            handleAttemptFailure(copywritingId, attempt, exception);
        }
    }

    private void handleAttemptFailure(Long copywritingId, int attempt, Throwable exception) {
        if (!isStillPending(copywritingId)) {
            queuedCopywritingIds.remove(copywritingId);
            log.info("AI review no longer pending, retry cancelled, copywritingId: {}", copywritingId);
            return;
        }

        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 1L << Math.min(attempt - 1, 6));
        log.error(
                "Immediate AI review failed; retry scheduled, copywritingId: {}, attempt: {}, delaySeconds: {}, exceptionType: {}",
                copywritingId,
                attempt,
                delaySeconds,
                exception.getClass().getSimpleName());
        scheduleAttempt(copywritingId, attempt + 1, delaySeconds);
    }

    private boolean isStillPending(Long copywritingId) {
        try {
            if (copywritingMapper.selectById(copywritingId) == null) {
                return false;
            }
            return approvalFlowMapper.selectCount(
                    new LambdaQueryWrapper<CopywritingApprovalFlow>()
                            .eq(CopywritingApprovalFlow::getCopywritingId, copywritingId)
                            .eq(CopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.AI_PENDING.getCode())) > 0;
        } catch (RuntimeException queryException) {
            log.warn("Unable to verify AI pending state; retaining retry, copywritingId: {}, exceptionType: {}",
                    copywritingId, queryException.getClass().getSimpleName());
            return true;
        }
    }

    @PreDestroy
    public void shutdownRetryScheduler() {
        retryScheduler.shutdownNow();
    }

    public void triggerApproval(Long copywritingId) {
        if (copywritingId == null) {
            throw new IllegalArgumentException("文案ID不能为空");
        }

        CopywritingApprovalFlow flow = approvalFlowMapper.selectOne(
                new LambdaQueryWrapper<CopywritingApprovalFlow>()
                        .eq(CopywritingApprovalFlow::getCopywritingId, copywritingId)
                        .eq(CopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.AI_PENDING.getCode())
        );

        if (flow == null || flow.getCopywritingApprovalFlowId() == null) {
            throw new IllegalStateException("当前文案没有待AI审核流程");
        }

        Copywriting copywriting = copywritingMapper.selectById(copywritingId);
        if (copywriting == null) {
            throw new IllegalStateException("文案不存在");
        }
        if (copywriting.getContent() == null || copywriting.getContent().isBlank()) {
            throw new IllegalStateException("文案内容为空，无法进行AI审核");
        }

        AIContentReviewResultEntity review = aiPort.reviewContent(copywriting.getContent());
        String comment = buildComment(review);

        OperatedCopywritingApprovalFlowAggregate aggregate = OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(
                        CopywritingApprovalFlowEntity.builder()
                                .copywritingApprovalFlowId(flow.getCopywritingApprovalFlowId())
                                .currentStatus(ApprovalStatusEnumVO.getByCode(flow.getCurrentStatus()))
                                .build()
                )
                .copywritingId(copywritingId)
                .operatorUserId(AI_OPERATOR_ID)
                .comment(comment)
                .build();

        if (review == null) {
            throw new IllegalStateException("AI review returned no result");
        }
        if (Boolean.TRUE.equals(review.getPass())) {
            copyWritingApprovalService.approvalByAI(aggregate);
        } else {
            copyWritingApprovalService.reject(aggregate);
        }
    }

    private String buildComment(AIContentReviewResultEntity review) {
        if (review == null) return "AI审核失败：无结果";

        StringBuilder sb = new StringBuilder();
        sb.append("AI审核结果：").append(Boolean.TRUE.equals(review.getPass()) ? "通过" : "不通过").append("；");

        if (review.getSensitiveWords() != null && !review.getSensitiveWords().isEmpty()) {
            sb.append("敏感词=").append(review.getSensitiveWords()).append("；");
        }
        if (review.getTypos() != null && !review.getTypos().isEmpty()) {
            sb.append("错别字=").append(review.getTypos()).append("；");
        }
        if (review.getScientificIssues() != null && !review.getScientificIssues().isEmpty()) {
            sb.append("科学性问题=").append(review.getScientificIssues()).append("；");
        }
        if (review.getSuggestion() != null && !review.getSuggestion().isBlank()) {
            sb.append("建议=").append(review.getSuggestion());
        }
        // 避免超长入库
        String result = sb.toString();
        if (result.length() > 900) {
            result = result.substring(0, 900);
        }
        log.info("AI审核结果解析完成，结果长度: {}", result.length());
        return result;
    }
}
