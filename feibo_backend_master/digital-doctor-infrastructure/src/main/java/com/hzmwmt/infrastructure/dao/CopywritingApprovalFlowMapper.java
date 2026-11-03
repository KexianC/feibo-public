package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;
import com.hzmwmt.infrastructure.dao.po.CopywritingApprovalFlow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingApprovalFlowMapper
 * @Date: 2025/9/3 17:24
 */
public interface CopywritingApprovalFlowMapper extends BaseMapper<CopywritingApprovalFlow> {

    int updateApprovalFlowStatus2Expert(CopywritingApprovalFlow build);

    int updateApprovalFlowStatus2AI(CopywritingApprovalFlow build);

    int updateApprovalFlowStatus2PlatForm(CopywritingApprovalFlow build);

    int updateApprovalFlowStatus2Complete(CopywritingApprovalFlow build);

    int updateApprovalFlowStatus2Rejected(CopywritingApprovalFlow build);

    CopywritingApprovalFlow selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(Long approvalFlowId);

    int updateApprovalFlowCurrentProcessorId2Assistant(DelegateCommandEntity commandEntity);

    int updateApprovalFlowCurrentProcessorIdOnPlatForm(CopywritingApprovalFlow build);

    int updateApprovalFlowCurrentProcessorIdOnExpert(CopywritingApprovalFlow build);

    List<CopywritingApprovalFlow> selectCopywritingApprovalFlowListPageByCopywritingIdsAndApprovalStatus(@Param("copywritingIds") List<Long> copywritingIds, @Param("approvalStatus") Integer approvalStatus, @Param("offset") Long offset, @Param("size") Integer size);

    List<CopywritingApprovalFlow> selectCopywritingApprovalFlowListPageByCopywritingIds(List<Long> copywritingIds);

    CopywritingApprovalFlow selectLatestByCopywritingId(@Param("copywritingId") Long copywritingId);

    List<CopywritingApprovalFlow> selectVisibleApprovalFlowsPage(
            @Param("actorUserId") Long actorUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization,
            @Param("approvalStatus") Integer approvalStatus,
            @Param("offset") long offset,
            @Param("size") Integer size);

    long countVisibleApprovalFlows(
            @Param("actorUserId") Long actorUserId,
            @Param("allAccess") boolean allAccess,
            @Param("reviewExpert") boolean reviewExpert,
            @Param("reviewOrganization") boolean reviewOrganization,
            @Param("approvalStatus") Integer approvalStatus);
}
