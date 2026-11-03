package com.hzmwmt.trigger.job;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.model.entity.AIContentReviewResultEntity;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.ICopyWritingApprovalService;
import com.hzmwmt.infrastructure.dao.CopywritingApprovalFlowMapper;
import com.hzmwmt.infrastructure.dao.CopywritingMapper;
import com.hzmwmt.infrastructure.dao.po.Copywriting;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopywritingAiApprovalJobTest {

    private static final long COPYWRITING_ID = 101L;
    private static final long FLOW_ID = 202L;

    @Mock
    private CopywritingApprovalFlowMapper approvalFlowMapper;
    @Mock
    private CopywritingMapper copywritingMapper;
    @Mock
    private IAIPort aiPort;
    @Mock
    private ICopyWritingApprovalService approvalService;

    private CopywritingAiApprovalJob job;

    @BeforeEach
    void setUp() {
        job = new CopywritingAiApprovalJob();
        ReflectionTestUtils.setField(job, "approvalFlowMapper", approvalFlowMapper);
        ReflectionTestUtils.setField(job, "copywritingMapper", copywritingMapper);
        ReflectionTestUtils.setField(job, "aiPort", aiPort);
        ReflectionTestUtils.setField(job, "copyWritingApprovalService", approvalService);

        lenient().when(approvalFlowMapper.selectOne(any(Wrapper.class))).thenReturn(
                CopywritingApprovalFlow.builder()
                        .copywritingApprovalFlowId(FLOW_ID)
                        .copywritingId(COPYWRITING_ID)
                        .currentStatus(ApprovalStatusEnumVO.AI_PENDING.getCode())
                        .build());
        lenient().when(copywritingMapper.selectById(COPYWRITING_ID)).thenReturn(
                Copywriting.builder()
                        .copywritingId(COPYWRITING_ID)
                        .content("用于测试的医学科普内容")
                        .build());
    }

    @Test
    void passesAiApprovedContentToExpertStage() {
        when(aiPort.reviewContent(any())).thenReturn(
                AIContentReviewResultEntity.builder().pass(true).suggestion("通过").build());

        job.triggerApproval(COPYWRITING_ID);

        verify(approvalService).approvalByAI(any(OperatedCopywritingApprovalFlowAggregate.class));
        verify(approvalService, never()).reject(any());
    }

    @Test
    void rejectsContentWhenAiReviewDoesNotPass() {
        when(aiPort.reviewContent(any())).thenReturn(
                AIContentReviewResultEntity.builder()
                        .pass(false)
                        .scientificIssues(java.util.List.of("存在医学事实风险"))
                        .build());

        job.triggerApproval(COPYWRITING_ID);

        ArgumentCaptor<OperatedCopywritingApprovalFlowAggregate> captor =
                ArgumentCaptor.forClass(OperatedCopywritingApprovalFlowAggregate.class);
        verify(approvalService).reject(captor.capture());
        verify(approvalService, never()).approvalByAI(any());
        assertThat(captor.getValue().getOperatorUserId()).isZero();
        assertThat(captor.getValue().getApprovalFlowEntity().getCurrentStatus())
                .isEqualTo(ApprovalStatusEnumVO.AI_PENDING);
        assertThat(captor.getValue().getComment()).contains("不通过", "医学事实风险");
    }

    @Test
    void stopsRetryingWhenCopywritingWasDeleted() {
        when(copywritingMapper.selectById(COPYWRITING_ID)).thenReturn(null);

        Boolean stillPending = ReflectionTestUtils.invokeMethod(job, "isStillPending", COPYWRITING_ID);

        assertThat(stillPending).isFalse();
    }
}
