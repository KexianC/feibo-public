package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalHistory;
import com.hzmwmt.infrastructure.dao.po.CopywritingAuditRecordView;
import com.hzmwmt.infrastructure.dao.po.Copywriting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingApprovalHistoryMapper
 * @Date: 2025/9/3 17:25
 */
public interface CopywritingApprovalHistoryMapper extends BaseMapper<CopywritingApprovalHistory> {

    List<CopywritingApprovalHistory> selectApprovalHistoryListByCopywritingIds(List<Long> copywritingApprovalFlowIds);

    Copywriting selectVisibleCopywritingById(
            @Param("copywritingId") Long copywritingId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization);

    long countExpertAssignmentForCopywriting(
            @Param("copywritingId") Long copywritingId,
            @Param("viewerUserId") Long viewerUserId);

    long countOrganizationAssignmentForCopywriting(
            @Param("copywritingId") Long copywritingId,
            @Param("viewerUserId") Long viewerUserId);

    long countVisibleAuditRecords(
            @Param("viewerUserId") Long viewerUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization,
            @Param("copywritingId") Long copywritingId,
            @Param("auditorStage") Integer auditorStage,
            @Param("approved") Boolean approved);

    List<CopywritingAuditRecordView> selectVisibleAuditRecords(
            @Param("viewerUserId") Long viewerUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization,
            @Param("copywritingId") Long copywritingId,
            @Param("auditorStage") Integer auditorStage,
            @Param("approved") Boolean approved,
            @Param("offset") long offset,
            @Param("size") Integer size);

    List<CopywritingAuditRecordView> selectVisibleAuditRecordsByCopywritingId(
            @Param("copywritingId") Long copywritingId,
            @Param("viewerUserId") Long viewerUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization);
}
