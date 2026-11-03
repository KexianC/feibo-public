package com.hzmwmt.security;

import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.ClaimTaskEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.infrastructure.adapter.repository.CopyWritingRepository;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalFlow;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalHistory;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CopyWritingRepositoryScopeWriteTest {

    private final CopywritingApprovalFlowMapper flowMapper = mock(CopywritingApprovalFlowMapper.class);
    private final CopywritingApprovalHistoryMapper historyMapper = mock(CopywritingApprovalHistoryMapper.class);
    private final CopywritingExpertAssignmentMapper expertAssignmentMapper = mock(CopywritingExpertAssignmentMapper.class);
    private final CopywritingOrganizationAdminAssignmentMapper organizationAssignmentMapper =
            mock(CopywritingOrganizationAdminAssignmentMapper.class);
    private final UserRoleMapMapper userRoleMapMapper = mock(UserRoleMapMapper.class);
    private CopyWritingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CopyWritingRepository();
        ReflectionTestUtils.setField(repository, "approvalFlowMapper", flowMapper);
        ReflectionTestUtils.setField(repository, "copywritingApprovalFlowMapper", flowMapper);
        ReflectionTestUtils.setField(repository, "approvalHistoryMapper", historyMapper);
        ReflectionTestUtils.setField(repository, "copywritingExpertAssignmentMapper", expertAssignmentMapper);
        ReflectionTestUtils.setField(repository, "copywritingOrganizationAdminAssignmentMapper", organizationAssignmentMapper);
        ReflectionTestUtils.setField(repository, "userRoleMapMapper", userRoleMapMapper);
    }

    @Test
    void userACannotApproveTaskClaimedByUserB() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(501L))
                .thenReturn(flow(501L, 1001L, 1, 302L));

        AppException failure = assertThrows(AppException.class,
                () -> repository.approvalByExpert(operation(
                        501L, 1001L, 301L, 302L,
                        new CopywritingDataScope(301L, false, true, false))));

        assertEquals(ResponseCode.E008.getCode(), failure.getCode());
        verify(flowMapper, never()).updateApprovalFlowStatus2PlatForm(any());
        verifyNoInteractions(expertAssignmentMapper);
    }

    @Test
    void unclaimedExpertTaskCannotBeApprovedBeforeClaim() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(501L))
                .thenReturn(flow(501L, 1001L, 1, null));

        AppException failure = assertThrows(AppException.class,
                () -> repository.approvalByExpert(operation(
                        501L, 1001L, 301L, null,
                        new CopywritingDataScope(301L, false, true, false))));

        assertEquals(ResponseCode.E008.getCode(), failure.getCode());
        verify(flowMapper, never()).updateApprovalFlowStatus2PlatForm(any());
    }

    @Test
    void claimedAndAssignedExpertCanApproveWithAllConditionsPreserved() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(501L))
                .thenReturn(flow(501L, 1001L, 1, 301L));
        when(expertAssignmentMapper.selectActiveExpertUserIdForUpdate(301L)).thenReturn(301L);
        when(userRoleMapMapper.selectExpertRoleMapIdForUpdate(301L)).thenReturn(71L);
        when(expertAssignmentMapper.selectAssignmentIdForUpdate(1001L, 301L)).thenReturn(81L);
        when(flowMapper.updateApprovalFlowStatus2PlatForm(any())).thenReturn(1);
        when(historyMapper.insert(any(CopywritingApprovalHistory.class))).thenReturn(1);

        repository.approvalByExpert(operation(
                501L, 1001L, 301L, 301L,
                new CopywritingDataScope(301L, false, true, false)));

        ArgumentCaptor<CopywritingApprovalFlow> update = ArgumentCaptor.forClass(CopywritingApprovalFlow.class);
        verify(flowMapper).updateApprovalFlowStatus2PlatForm(update.capture());
        assertEquals(501L, update.getValue().getCopywritingApprovalFlowId());
        assertEquals(1001L, update.getValue().getCopywritingId());
        assertEquals(301L, update.getValue().getCurrentProcessorId());
        assertFalse(Boolean.TRUE.equals(update.getValue().getGlobalAccess()));
    }

    @Test
    void crossOrganizationAdminWithoutExplicitAssignmentIsDeniedWithoutExistenceLeak() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(601L))
                .thenReturn(flow(601L, 2001L, 3, 401L));
        when(organizationAssignmentMapper.selectActiveAdminUserIdForUpdate(401L)).thenReturn(401L);
        when(organizationAssignmentMapper.selectOrganizationAdminRoleMapIdForUpdate(401L)).thenReturn(91L);
        when(organizationAssignmentMapper.selectAssignmentIdForUpdate(2001L, 401L)).thenReturn(null);

        AppException unauthorized = assertThrows(AppException.class,
                () -> repository.approvalByPlatform(operation(
                        601L, 2001L, 401L, 401L,
                        new CopywritingDataScope(401L, false, false, true))));
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(602L)).thenReturn(null);
        AppException missing = assertThrows(AppException.class,
                () -> repository.approvalByPlatform(operation(
                        602L, 2002L, 401L, 401L,
                        new CopywritingDataScope(401L, false, false, true))));

        assertEquals(ResponseCode.E008.getCode(), unauthorized.getCode());
        assertEquals(unauthorized.getCode(), missing.getCode());
        verify(flowMapper, never()).updateApprovalFlowStatus2Complete(any());
    }

    @Test
    void activeAdminCanOperateAllWithoutPretendingToBeAssignedExpert() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(501L))
                .thenReturn(flow(501L, 1001L, 1, 302L));
        when(userRoleMapMapper.selectGlobalAdminRoleMapIdForUpdate(900L)).thenReturn(99L);
        when(flowMapper.updateApprovalFlowStatus2PlatForm(any())).thenReturn(1);
        when(historyMapper.insert(any(CopywritingApprovalHistory.class))).thenReturn(1);

        repository.approvalByExpert(operation(
                501L, 1001L, 900L, 302L,
                new CopywritingDataScope(900L, true, false, false)));

        ArgumentCaptor<CopywritingApprovalFlow> update = ArgumentCaptor.forClass(CopywritingApprovalFlow.class);
        verify(flowMapper).updateApprovalFlowStatus2PlatForm(update.capture());
        assertTrue(Boolean.TRUE.equals(update.getValue().getGlobalAccess()));
        assertEquals(900L, update.getValue().getScopeActorId());
        verifyNoInteractions(expertAssignmentMapper);
    }

    @Test
    void concurrentExpertClaimLoserFailsWhenAtomicUpdateAffectsNoRow() {
        when(flowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(501L))
                .thenReturn(flow(501L, 1001L, 1, null));
        when(expertAssignmentMapper.selectActiveExpertUserIdForUpdate(301L)).thenReturn(301L);
        when(userRoleMapMapper.selectExpertRoleMapIdForUpdate(301L)).thenReturn(71L);
        when(expertAssignmentMapper.selectAssignmentIdForUpdate(1001L, 301L)).thenReturn(81L);
        when(flowMapper.updateApprovalFlowCurrentProcessorIdOnExpert(any())).thenReturn(0);

        AppException failure = assertThrows(AppException.class,
                () -> repository.claimApprovalTaskByExpert(ClaimTaskEntity.builder()
                        .copywritingApprovalFlowId(501L)
                        .operatorId(301L)
                        .dataScope(new CopywritingDataScope(301L, false, true, false))
                        .build()));

        assertEquals(ResponseCode.E010.getCode(), failure.getCode());
    }

    private OperatedCopywritingApprovalFlowAggregate operation(
            long flowId,
            long copywritingId,
            long actor,
            Long expectedProcessor,
            CopywritingDataScope scope) {
        return OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(CopywritingApprovalFlowEntity.builder()
                        .copywritingApprovalFlowId(flowId)
                        .build())
                .copywritingId(copywritingId)
                .operatorUserId(actor)
                .expectedProcessorId(expectedProcessor)
                .dataScope(scope)
                .comment("reviewed")
                .build();
    }

    private CopywritingApprovalFlow flow(long flowId, long copywritingId, int status, Long processor) {
        return CopywritingApprovalFlow.builder()
                .copywritingApprovalFlowId(flowId)
                .copywritingId(copywritingId)
                .currentStatus(status)
                .currentProcessorId(processor)
                .build();
    }
}
