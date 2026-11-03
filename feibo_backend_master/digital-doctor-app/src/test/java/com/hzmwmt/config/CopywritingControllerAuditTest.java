package com.hzmwmt.config;

import com.hzmwmt.api.dto.CopywritingAuditRecordResponseDTO;
import com.hzmwmt.api.response.ApiOffsetPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingAuditRecordEntity;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.ICopywritingManagerService;
import com.hzmwmt.trigger.http.client.CopywritingController;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class CopywritingControllerAuditTest {

    private static final CopywritingDataScope EXPERT_SCOPE =
            new CopywritingDataScope(202L, false, true, false);

    private final IAuthService authService = mock(IAuthService.class);
    private final ICopywritingManagerService managerService = mock(ICopywritingManagerService.class);
    private CopywritingController controller;

    @BeforeEach
    void setUp() {
        controller = new CopywritingController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "copywritingManagerService", managerService);
        when(authService.queryValidatedUser()).thenReturn(
                AuthUserEntity.builder().userId(202L).build());
        when(authService.hasExpertRole()).thenReturn(true);
        when(authService.hasOrganizationAdminRole()).thenReturn(false);
        when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void auditRecordsEndpointPreservesPaginationAndUsesServerSideViewerScope() {
        Date operationTime = new Date();
        CopywritingAuditRecordEntity record = CopywritingAuditRecordEntity.builder()
                .copywritingApprovalHistoryId(301L)
                .copywritingApprovalFlowId(401L)
                .copywritingId(101L)
                .copywritingTitle("完整文案")
                .operatorId(0L)
                .approved(true)
                .fromStatus(2)
                .toStatus(1)
                .operationTime(operationTime)
                .build();
        when(managerService.queryVisibleAuditRecords(
                EXPERT_SCOPE, 2, 5, 101L, 2, true))
                .thenReturn(OffsetPageResponseData.<CopywritingAuditRecordEntity>builder()
                        .data(List.of(record))
                        .hasNext(false)
                        .total(1L)
                        .page(2)
                        .size(5)
                        .build());

        Response<ApiOffsetPageData<CopywritingAuditRecordResponseDTO>> response =
                controller.queryAuditRecords(2, 5, 101L, "AI", "通过");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(1, response.getData().getTotal());
        assertEquals(2, response.getData().getPage());
        assertEquals(5, response.getData().getSize());
        assertFalse(response.getData().getHasNext());
        CopywritingAuditRecordResponseDTO dto = response.getData().getData().get(0);
        assertEquals(401L, dto.getCopywritingApprovalFlowId());
        assertEquals("AI 自动审核", dto.getOperatorName());
        assertEquals("AI", dto.getOperatorRole());
        verify(managerService).queryVisibleAuditRecords(
                EXPERT_SCOPE, 2, 5, 101L, 2, true);
    }
}
