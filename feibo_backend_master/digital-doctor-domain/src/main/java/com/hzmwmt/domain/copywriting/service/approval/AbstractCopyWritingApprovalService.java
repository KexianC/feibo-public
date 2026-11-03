package com.hzmwmt.domain.copywriting.service.approval;

import com.hzmwmt.domain.copywriting.adapter.port.ICopyWritingPort;
import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.CopyWritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CreateCopyWritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.ClaimTaskEntity;
import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.entity.DelegateCommandEntity;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.ICopyWritingApprovalService;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory.ApprovalEvent;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;

/**
 * @author huihui
 * @version v1.0
 * @Description: 文案审批抽象服务
 * @ClassName AbstractCopyWritingApprovalService
 * @Date: 2025/9/7 15:38
 */
@Slf4j
public abstract class AbstractCopyWritingApprovalService implements ICopyWritingApprovalService {
    @Resource
    protected ICopyWritingRepository copyWritingRepository;
    @Resource
    protected ICopyWritingPort copyWritingPort;
    @Resource
    private StateMachineFactory<ApprovalStatusEnumVO, ApprovalEvent> stateMachineFactory;

    @Override
    public Long submit(CreateCopyWritingAggregate createCopyWritingAggregate) {
        log.info("收到文案审批提交请求");
        // 具体实现由子类提供
        throw new UnsupportedOperationException("Subclass must implement copywriting submission");
    }

    @Override
    public void approvalByAI(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        log.info("AI审批文案，流程ID: {}", operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.AI_APPROVE);
    }

    @Override
    public void approvalByDoctor(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        log.info("作者自审确认文案，流程ID: {}", operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.DOCTOR_APPROVE);
    }

    @Override
    public void approvalByExpert(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        log.info("专家审批文案，流程ID: {}", operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.EXPERT_APPROVE);
    }

    @Override
    public void approvalByPlatform(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        log.info("所属医院/机构管理员终审文案，流程ID: {}", operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.PLATFORM_APPROVE);
    }

    @Override
    public void delegateToAssistant(DelegateCommandEntity delegateCommandEntity) {
        log.info("收到助手委派请求");
        // 具体实现由子类提供
    }

    @Override
    public void claimApprovalTaskByExpert(ClaimTaskEntity claimTaskEntity) {
        log.info("收到专家认领审批任务请求");
        // 具体实现由子类提供
    }

    @Override
    public void claimApprovalTaskByPlatform(ClaimTaskEntity claimTaskEntity) {
        log.info("收到所属医院/机构管理员认领终审任务请求");
        // 具体实现由子类提供
    }

    @Override
    public void reject(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        log.info("拒绝文案，流程ID: {}", operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity().getCopywritingApprovalFlowId());
        sendEvent(operatedCopywritingApprovalFlowAggregate, ApprovalEvent.REJECT);
    }

    protected void sendEvent(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate, ApprovalEvent event) {
        StateMachine<ApprovalStatusEnumVO, ApprovalEvent> stateMachine = null;
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        try {
            // 从工厂获取 stateMachine 实例
            stateMachine = stateMachineFactory.getStateMachine(String.valueOf(copywritingApprovalFlowId));
            CopyWritingApprovalFlowAggregate currentFlow = copyWritingRepository.queryApprovalFlow(copywritingApprovalFlowId);

            if (currentFlow == null) {
                log.error("审批流程不存在: {}", copywritingApprovalFlowId);
                throw operationConflict(event);
            }

            // 对状态机进行初始化
            ApprovalStatusEnumVO initialState = currentFlow.getApprovalFlowEntity().getCurrentStatus();
            log.info("对状态机进行初始化，初始状态: {}", initialState);

            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(sma -> sma.resetStateMachine(
                            new DefaultStateMachineContext<>(initialState, null, null, null))
                    );

            // 启动状态机
            log.info("启动状态机");
            stateMachine.start();

            // 获取真实的操作人ID
            Long actualOperatorId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();

            // 获取当前流程中实际绑定的处理人ID
            Long processorId = currentFlow.getOperatorUserId();
            operatedCopywritingApprovalFlowAggregate.setExpectedProcessorId(processorId);

            Message<ApprovalEvent> message = MessageBuilder.withPayload(event)
                    .setHeader("expectedProcessorId", processorId) // 使用流程中实际绑定的处理人ID
                    .setHeader("operatorId", actualOperatorId) // 使用真实操作人ID作为操作者
                    .setHeader("operatedFlow", operatedCopywritingApprovalFlowAggregate)
                    .build();

            // 发送事件，状态机会基于我们刚刚重置的状态，去执行 Guard 和 Action
            log.info("发送事件到状态机: {}, 初始状态: {}, 操作人ID: {}",
                    event.name(), initialState, actualOperatorId);

            boolean result = stateMachine.sendEvent(message);
            log.info("状态机事件发送结果: {}", result);

            // 检查结果
            if (!result) {
                // 如果 result 为 false，说明 Guard 判断失败或 Transition 不存在
                log.error("审批流[{}]状态流转失败！数据库当前状态: {}, 尝试事件: {}, 可能的原因: Guard判断失败或Transition不存在",
                        copywritingApprovalFlowId, initialState, event.name());
                throw operationConflict(event);
            } else {
                log.info("审批流[{}]状态流转成功，初始状态: {}, 执行事件: {}",
                        copywritingApprovalFlowId, initialState, event.name());
            }
        } catch (Exception e) {
            log.error("状态机操作失败: approvalFlowId={}, event={}, code={}, type={}",
                    copywritingApprovalFlowId, event.name(), operationConflictCode(event), exceptionType(e));
            // 包装异常，确保事务回滚
            throw e;
        } finally {
            // 停止状态机
            if (stateMachine != null) {
                stateMachine.stop();
            }
        }
    }

    private AppException operationConflict(ApprovalEvent event) {
        return new AppException(event == ApprovalEvent.REJECT ? ResponseCode.E009 : ResponseCode.E008);
    }

    private String operationConflictCode(ApprovalEvent event) {
        return (event == ApprovalEvent.REJECT ? ResponseCode.E009 : ResponseCode.E008).getCode();
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }
}
