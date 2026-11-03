package com.hzmwmt.security;

import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.CopyWritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.approval.CopyWritingApprovalService;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory.ApprovalEvent;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.access.StateMachineAccessor;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CopyWritingApprovalStateMachineDisclosureTest {

    private final ICopyWritingRepository repository = mock(ICopyWritingRepository.class);
    private final StateMachineFactory<ApprovalStatusEnumVO, ApprovalEvent> stateMachineFactory =
            mock(StateMachineFactory.class);
    private final StateMachine<ApprovalStatusEnumVO, ApprovalEvent> stateMachine = mock(StateMachine.class);
    private final StateMachineAccessor<ApprovalStatusEnumVO, ApprovalEvent> accessor =
            mock(StateMachineAccessor.class);
    private CopyWritingApprovalService service;

    @BeforeEach
    void setUp() {
        service = new CopyWritingApprovalService();
        ReflectionTestUtils.setField(service, "copyWritingRepository", repository);
        ReflectionTestUtils.setField(service, "stateMachineFactory", stateMachineFactory);
        when(stateMachineFactory.getStateMachine(anyString())).thenReturn(stateMachine);
        when(stateMachine.getStateMachineAccessor()).thenReturn(accessor);
        when(stateMachine.sendEvent(any(Message.class))).thenReturn(false);
    }

    @Test
    void missingAndUnauthorizedApprovalReturnTheSameOpaqueConflict() {
        AppException missing = assertThrows(AppException.class,
                () -> service.approvalByExpert(operation(501L, 301L)));

        when(repository.queryApprovalFlow(502L)).thenReturn(currentFlow(502L, 302L));
        AppException unauthorized = assertThrows(AppException.class,
                () -> service.approvalByExpert(operation(502L, 301L)));

        assertEquals(ResponseCode.E008.getCode(), missing.getCode());
        assertEquals(missing.getCode(), unauthorized.getCode());
    }

    @Test
    void missingAndUnauthorizedRejectionReturnTheSameOpaqueConflict() {
        AppException missing = assertThrows(AppException.class,
                () -> service.reject(operation(601L, 401L)));

        when(repository.queryApprovalFlow(602L)).thenReturn(currentFlow(602L, 402L));
        AppException unauthorized = assertThrows(AppException.class,
                () -> service.reject(operation(602L, 401L)));

        assertEquals(ResponseCode.E009.getCode(), missing.getCode());
        assertEquals(missing.getCode(), unauthorized.getCode());
    }

    @Test
    void delegationRemainsClosedAtTheDomainBoundary() {
        AppException failure = assertThrows(AppException.class,
                () -> service.delegateToAssistant(DelegateCommandEntity.builder()
                        .approvalFlowId(701L)
                        .expertId(501L)
                        .assistantId(502L)
                        .build()));

        assertEquals(ResponseCode.FORBIDDEN.getCode(), failure.getCode());
        verifyNoInteractions(repository);
    }

    private OperatedCopywritingApprovalFlowAggregate operation(long flowId, long actor) {
        return OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(CopywritingApprovalFlowEntity.builder()
                        .copywritingApprovalFlowId(flowId)
                        .build())
                .copywritingId(1001L)
                .operatorUserId(actor)
                .dataScope(new CopywritingDataScope(actor, false, true, false))
                .build();
    }

    private CopyWritingApprovalFlowAggregate currentFlow(long flowId, long processor) {
        return CopyWritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(CopywritingApprovalFlowEntity.builder()
                        .copywritingApprovalFlowId(flowId)
                        .currentStatus(ApprovalStatusEnumVO.EXPERT_PENDING)
                        .build())
                .operatorUserId(processor)
                .build();
    }
}
