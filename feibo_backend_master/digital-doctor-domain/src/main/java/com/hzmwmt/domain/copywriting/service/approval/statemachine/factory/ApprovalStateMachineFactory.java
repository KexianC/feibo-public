package com.hzmwmt.domain.copywriting.service.approval.statemachine.factory;

import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.ApprovalAction;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.ApprovalGuard;
import com.hzmwmt.domain.copywriting.service.approval.statemachine.ApprovalListener;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalStateMachineFactory
 * @Date: 2025/9/7 14:09
 */
@Configuration
@EnableStateMachineFactory
public class ApprovalStateMachineFactory extends EnumStateMachineConfigurerAdapter<ApprovalStatusEnumVO, ApprovalStateMachineFactory.ApprovalEvent> {
    @Resource
    ApprovalAction approvalAction;
    @Resource
    ApprovalGuard approvalGuard;
    @Resource
    ApprovalListener approvalListener;

    @Override
    public void configure(StateMachineStateConfigurer<ApprovalStatusEnumVO, ApprovalEvent> states) throws Exception {
        states.withStates()
                .initial(ApprovalStatusEnumVO.DOCTOR_PENDING)
                .states(EnumSet.allOf(ApprovalStatusEnumVO.class));
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<ApprovalStatusEnumVO, ApprovalEvent> config) throws Exception {
        config
                .withConfiguration()
                .listener(approvalListener)
                .autoStartup(true);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ApprovalStatusEnumVO, ApprovalEvent> transitions) throws Exception {
        transitions
                // 作者自审/确认通过: 待作者确认 -> AI 待审核
                .withExternal()
                .source(ApprovalStatusEnumVO.DOCTOR_PENDING)
                .target(ApprovalStatusEnumVO.AI_PENDING)
                .event(ApprovalEvent.DOCTOR_APPROVE)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                // 专家批准: 待专家审批 -> 所属机构待终审
                .withExternal()
                .source(ApprovalStatusEnumVO.EXPERT_PENDING)
                .target(ApprovalStatusEnumVO.PLATFORM_PENDING)
                .event(ApprovalEvent.EXPERT_APPROVE)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                // AI 审核通过: AI 待审核 -> 专家待审批（不走处理器/权限校验）
                .withExternal()
                .source(ApprovalStatusEnumVO.AI_PENDING)
                .target(ApprovalStatusEnumVO.EXPERT_PENDING)
                .event(ApprovalEvent.AI_APPROVE)
                .action(approvalAction)
                .and()
                // 所属机构管理员批准: 机构待终审 -> 通过
                .withExternal()
                .source(ApprovalStatusEnumVO.PLATFORM_PENDING)
                .target(ApprovalStatusEnumVO.COMPLETE)
                .event(ApprovalEvent.PLATFORM_APPROVE)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                //驳回路径
                .withExternal()
                .source(ApprovalStatusEnumVO.DOCTOR_PENDING)
                .target(ApprovalStatusEnumVO.FAILURE)
                .event(ApprovalEvent.REJECT)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                .withExternal()
                .source(ApprovalStatusEnumVO.EXPERT_PENDING)
                .target(ApprovalStatusEnumVO.FAILURE)
                .event(ApprovalEvent.REJECT)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                .withExternal()
                .source(ApprovalStatusEnumVO.AI_PENDING)
                .target(ApprovalStatusEnumVO.FAILURE)
                .event(ApprovalEvent.REJECT)
                .guard(approvalGuard)
                .action(approvalAction)
                .and()
                .withExternal()
                .source(ApprovalStatusEnumVO.PLATFORM_PENDING)
                .target(ApprovalStatusEnumVO.FAILURE)
                .guard(approvalGuard)
                .action(approvalAction)
                .event(ApprovalEvent.REJECT);
    }


    public enum ApprovalEvent {
        AI_APPROVE,
        DOCTOR_APPROVE,
        EXPERT_APPROVE,
        PLATFORM_APPROVE,
        REJECT
    }
}
