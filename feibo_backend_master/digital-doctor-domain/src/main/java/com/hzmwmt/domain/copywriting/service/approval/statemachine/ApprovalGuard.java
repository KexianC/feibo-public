package com.hzmwmt.domain.copywriting.service.approval.statemachine;

import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory;
import jakarta.annotation.Resource;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalGuard
 * @Date: 2025/9/8 14:01
 */
@Component
public class ApprovalGuard implements Guard<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> {

    @Resource
    private AuthService authService;

    @Override
    public boolean evaluate(StateContext<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> context) {
        Long expectedProcessorId = context.getMessage().getHeaders().get("expectedProcessorId", Long.class);
        Long operatorId = context.getMessage().getHeaders().get("operatorId", Long.class);
        OperatedCopywritingApprovalFlowAggregate operatedFlow = context.getMessage().getHeaders()
                .get("operatedFlow", OperatedCopywritingApprovalFlowAggregate.class);
        ApprovalStateMachineFactory.ApprovalEvent event = context.getEvent();
        if (event == null) {
            return false;
        }
        return evaluateEvent(context, event, expectedProcessorId, operatorId, operatedFlow);
    }
    private boolean evaluateEvent(
            StateContext<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> context,
            ApprovalStateMachineFactory.ApprovalEvent event,
            Long expectedProcessorId,
            Long operatorId,
            OperatedCopywritingApprovalFlowAggregate operatedFlow) {
        if (operatorId == null) {
            return false;
        }

        ApprovalStatusEnumVO sourceStatus = context.getSource() == null ? null : context.getSource().getId();
        if (event == ApprovalStateMachineFactory.ApprovalEvent.REJECT
                && sourceStatus == ApprovalStatusEnumVO.AI_PENDING) {
            // AI 审核不通过由系统操作人执行；AI 阶段没有人工处理人。
            return operatorId == 0L && expectedProcessorId == null;
        }
        CopywritingDataScope dataScope = operatedFlow == null ? null : operatedFlow.getDataScope();
        if (dataScope == null || !Objects.equals(dataScope.actorUserId(), operatorId)) {
            return false;
        }
        if (dataScope.allAccess()) {
            return true;
        }
        if (event == ApprovalStateMachineFactory.ApprovalEvent.EXPERT_APPROVE
                || (event == ApprovalStateMachineFactory.ApprovalEvent.REJECT
                && sourceStatus == ApprovalStatusEnumVO.EXPERT_PENDING)) {
            return requireAssignment(
                    dataScope.expertReviewer() && Boolean.TRUE.equals(authService.hasExpertRole()),
                    expectedProcessorId,
                    operatorId);
        }

        if (event == ApprovalStateMachineFactory.ApprovalEvent.DOCTOR_APPROVE) {
            // “医生阶段”现为作者自审/确认，只有文案作者本人可确认，不要求任何角色。
            return requireAssignment(true, expectedProcessorId, operatorId);
        }
        if (event == ApprovalStateMachineFactory.ApprovalEvent.REJECT
                && sourceStatus == ApprovalStatusEnumVO.DOCTOR_PENDING) {
            // 作者确认前的驳回/撤回也只能由作者本人操作。
            return requireAssignment(true, expectedProcessorId, operatorId);
        }
        if (event == ApprovalStateMachineFactory.ApprovalEvent.PLATFORM_APPROVE
                || (event == ApprovalStateMachineFactory.ApprovalEvent.REJECT
                && sourceStatus == ApprovalStatusEnumVO.PLATFORM_PENDING)) {
            return requireAssignment(
                    dataScope.organizationReviewer()
                            && Boolean.TRUE.equals(authService.hasOrganizationAdminRole()),
                    expectedProcessorId,
                    operatorId);
        }
        if (event == ApprovalStateMachineFactory.ApprovalEvent.REJECT) {
            return false;
        }
        return false;
    }

    private boolean requireAssignment(boolean hasRequiredRole, Long expectedProcessorId, Long operatorId) {
        return hasRequiredRole
                && expectedProcessorId != null
                && Objects.equals(expectedProcessorId, operatorId);
    }
}
