package com.hzmwmt.domain.copywriting.service.approval.statemachine;

import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.factory.ApprovalStateMachineFactory;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalListener
 * @Date: 2025/9/25 17:12
 */
@Component
public class ApprovalListener extends StateMachineListenerAdapter<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> {

    /**
     * 状态机内部任何地方抛出异常都会走到这里
     */
    @Override
    public void stateMachineError(StateMachine<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> stateMachine,
                                  Exception exception) {
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }


}