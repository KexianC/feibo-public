package com.hzmwmt.domain.copywriting.service.approval.statemachine;

import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalAction
 * @Date: 2025/9/8 14:36
 */
@Slf4j
@Component
public class ApprovalAction implements Action<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> {
    @Resource
    private ICopyWritingRepository copyWritingRepository;

    @Override
    public void execute(StateContext<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> context) {
        OperatedCopywritingApprovalFlowAggregate operatedFlow = context.getMessage().getHeaders().get("operatedFlow", OperatedCopywritingApprovalFlowAggregate.class);
        log.info("执行状态机:{}", operatedFlow.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        ApprovalStateMachineFactory.ApprovalEvent event = context.getEvent();
        try {
            switch (event) {
                case AI_APPROVE:
                    log.info("执行 AI 审核通过操作");
                    copyWritingRepository.approvalByAI(operatedFlow);
                    break;
                case DOCTOR_APPROVE:
                    log.info("执行作者自审确认操作");
                    copyWritingRepository.approvalByDoctor(operatedFlow);
                    break;
                case EXPERT_APPROVE:
                    log.info("执行专家审批操作");
                    copyWritingRepository.approvalByExpert(operatedFlow);
                    break;
                case PLATFORM_APPROVE:
                    log.info("执行所属医院/机构管理员终审操作");
                    copyWritingRepository.approvalByPlatform(operatedFlow);
                    break;
                case REJECT:
                    log.info("执行驳回操作");
                    copyWritingRepository.reject(operatedFlow);
                    break;
                default:
                    log.warn("在 Action 中收到了一个未处理的事件: {}", event.name());
                    return;
            }
            log.info("执行状态机成功:{}", operatedFlow.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        } catch (Exception e) {
            log.error("执行状态机操作时发生异常，event={}, type={}",
                    event == null ? "UNKNOWN" : event.name(), e.getClass().getSimpleName());
            // 重新抛出异常，确保状态机能够感知到操作失败
            throw e;
        }
    }
}
