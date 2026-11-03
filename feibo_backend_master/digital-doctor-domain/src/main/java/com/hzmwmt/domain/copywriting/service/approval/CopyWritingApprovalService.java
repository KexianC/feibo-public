package com.hzmwmt.domain.copywriting.service.approval;

import com.hzmwmt.domain.copywriting.model.aggregate.CreateCopyWritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.ClaimTaskEntity;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;
import com.hzmwmt.domain.copywriting.model.entity.MessageEntity;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory.ApprovalEvent;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopyWritingApprovalService
 * @Date: 2025/9/7 15:34
 */
@Slf4j
@Service
public class CopyWritingApprovalService extends AbstractCopyWritingApprovalService {
    @Override
    public Long submit(CreateCopyWritingAggregate copyWritingAggregate) {
        Long copywritingId = copyWritingRepository.submit(copyWritingAggregate);
        copyWritingPort.sendApprovalStateMessage(
                MessageEntity.builder()
                        .userId(copyWritingAggregate.getUserId())
                        .title("审批流程")
                        .content("文案创建成功")
                        .createTime(new Date())
                        .build()
        );
        return copywritingId;
    }

    @Override
    public void approvalByAI(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.AI_APPROVE);
    }

    @Override
    public void approvalByDoctor(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.DOCTOR_APPROVE);

    }

    @Override
    public void approvalByExpert(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.EXPERT_APPROVE);
    }

    @Override
    public void approvalByPlatform(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.PLATFORM_APPROVE);
    }

    @Override
    public void delegateToAssistant(DelegateCommandEntity delegateCommandEntity) {
        // No assistant-assignment relation exists in the current schema, so the
        // domain entry point remains closed even if it is called outside HTTP.
        throw new AppException(ResponseCode.FORBIDDEN);
    }

    @Override
    public void claimApprovalTaskByExpert(ClaimTaskEntity claimTaskEntity) {
        copyWritingRepository.claimApprovalTaskByExpert(claimTaskEntity);
    }

    @Override
    public void claimApprovalTaskByPlatform(ClaimTaskEntity claimTaskEntity) {
        copyWritingRepository.claimApprovalTaskByPlatForm(claimTaskEntity);
    }

    @Override
    public void reject(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.REJECT);
    }

}
