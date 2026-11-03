package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.domain.copywriting.adapter.repository.ICopyWritingRepository;
import com.hzmwmt.domain.copywriting.model.aggregate.*;
import com.hzmwmt.domain.copywriting.model.entity.*;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalOperationStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.*;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopyWritingRepository
 * @Date: 2025/9/7 13:13
 */
@Slf4j
@Repository
public class CopyWritingRepository extends AbstractRepository implements ICopyWritingRepository {
    @Resource
    private CopywritingMapper copywritingMapper;
    @Resource
    private CopywritingApprovalFlowMapper approvalFlowMapper;
    @Resource
    private CopywritingApprovalHistoryMapper approvalHistoryMapper;
    @Resource
    private CopywritingCategoryMapper copywritingCategoryMapper;
    @Resource
    private CopywritingSourceMapper copywritingSourceMapper;
    @Resource
    private CopywritingApprovalFlowMapper copywritingApprovalFlowMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserRoleMapMapper userRoleMapMapper;
    @Resource
    private CopywritingExpertAssignmentMapper copywritingExpertAssignmentMapper;
    @Resource
    private CopywritingOrganizationAdminAssignmentMapper copywritingOrganizationAdminAssignmentMapper;

    @Override
    @Transactional(timeout = 500)
    public Long submit(CreateCopyWritingAggregate copyWritingAggregate) {
        CopywritingEntity copywritingEntity = copyWritingAggregate.getCopywritingEntity();
        Copywriting build = Copywriting.builder()
                .title(copywritingEntity.getTitle())
                .userId(copyWritingAggregate.getUserId())
                .summary(copywritingEntity.getSummary())
                .content(copywritingEntity.getContent())
                .copywritingCategoryId(copyWritingAggregate.getCopywritingCategoryId())
                .copywritingSourceId(copyWritingAggregate.getCopywritingSourceId())
                .status(1)
                .build();
        if (copywritingMapper.insert(build) != 1 || build.getCopywritingId() == null) {
            throw new AppException(ResponseCode.SYSTEM_ERROR);
        }
        // 提交后先由作者本人自审/确认，确认通过后再触发 AI 审核。
        if (approvalFlowMapper.insert(
                CopywritingApprovalFlow.builder()
                        .copywritingId(build.getCopywritingId())
                        .currentStatus(ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())
                        .currentProcessorId(copyWritingAggregate.getUserId())
                        .build()
        ) != 1) {
            throw new AppException(ResponseCode.SYSTEM_ERROR);
        }

        return build.getCopywritingId();
    }


    @Override
    @Transactional(timeout = 500)
    public void approvalByDoctor(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        Long operatorUserId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();
        CopywritingDataScope dataScope = requireActorScope(
                operatedCopywritingApprovalFlowAggregate, ResponseCode.E008);
        boolean globalAccess = dataScope.allAccess();

        log.info("开始作者自审确认流程，流程ID: {}, 操作人ID: {}", copywritingApprovalFlowId, operatorUserId);

        // 先查询当前流程状态和处理者信息，确保状态匹配（作者确认阶段）
        CopywritingApprovalFlow currentFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(copywritingApprovalFlowId);
        log.info("查询到当前流程状态: {}, 当前处理者ID: {}", currentFlow != null ? currentFlow.getCurrentStatus() : "null",
                currentFlow != null ? currentFlow.getCurrentProcessorId() : "null");

        if (currentFlow == null) {
            log.error("流程不存在，流程ID: {}", copywritingApprovalFlowId);
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCurrentStatus(), ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())) {
            log.error("流程状态不匹配，期望状态: {}, 实际状态: {}",
                    ApprovalStatusEnumVO.DOCTOR_PENDING.getCode(), currentFlow.getCurrentStatus());
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCopywritingId(), operatedCopywritingApprovalFlowAggregate.getCopywritingId())) {
            log.error("作者确认请求与流程不匹配，流程ID: {}, 操作人ID: {}, 当前处理人ID: {}",
                    copywritingApprovalFlowId, operatorUserId, currentFlow.getCurrentProcessorId());
            throw new AppException(ResponseCode.E008);
        }
        if (globalAccess) {
            lockGlobalAdmin(operatorUserId, ResponseCode.E008);
        } else if (!Objects.equals(currentFlow.getCurrentProcessorId(), operatorUserId)
                || !Objects.equals(operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId(), operatorUserId)) {
            throw new AppException(ResponseCode.E008);
        }

        // 以流程、文案、状态和作者四个条件做 CAS，防止越权确认和重复提交。
        CopywritingApprovalFlow updateFlow = CopywritingApprovalFlow.builder()
                .copywritingApprovalFlowId(copywritingApprovalFlowId)
                .copywritingId(currentFlow.getCopywritingId())
                .currentProcessorId(currentFlow.getCurrentProcessorId())
                .globalAccess(globalAccess)
                .scopeActorId(operatorUserId)
                .build();

        log.info("执行状态更新，流程ID: {}", copywritingApprovalFlowId);
        int updateResult = approvalFlowMapper.updateApprovalFlowStatus2AI(updateFlow);
        log.info("状态更新结果: 影响行数 = {}", updateResult);

        if (updateResult != 1) {
            log.error("状态更新失败，影响行数不为1，抛出审批冲突异常");
            throw new AppException(ResponseCode.E008);
        } else {
            log.info("状态更新成功，开始插入审批历史记录");
            insertApprovalHistory(
                    CopywritingApprovalHistory.builder()
                            .operatorId(operatedCopywritingApprovalFlowAggregate.getOperatorUserId())
                            .copywritingApprovalFlowId(copywritingApprovalFlowId)
                            .fromStatus(ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())
                            .toStatus(ApprovalStatusEnumVO.AI_PENDING.getCode())
                            .operationStatus(ApprovalOperationStatusEnumVO.APPROVE.getCode())
                            .comment(operatedCopywritingApprovalFlowAggregate.getComment())
                            .build()
            );
        }
    }

    @Override
    @Transactional(timeout = 500)
    public void approvalByAI(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        Long operatorUserId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();

        log.info("开始AI审批流程，流程ID: {}, 操作人ID: {}", copywritingApprovalFlowId, operatorUserId);

        // 先查询当前流程状态，确保状态匹配（AI阶段）
        CopywritingApprovalFlow currentFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(copywritingApprovalFlowId);
        if (currentFlow == null) {
            log.error("流程不存在，流程ID: {}", copywritingApprovalFlowId);
            throw new AppException(ResponseCode.E008);
        }
        if (!Objects.equals(currentFlow.getCurrentStatus(), ApprovalStatusEnumVO.AI_PENDING.getCode())
                || !Objects.equals(currentFlow.getCopywritingId(), operatedCopywritingApprovalFlowAggregate.getCopywritingId())
                || !Objects.equals(operatorUserId, 0L)
                || currentFlow.getCurrentProcessorId() != null) {
            log.error("流程状态不匹配，期望状态: {}, 实际状态: {}",
                    ApprovalStatusEnumVO.AI_PENDING.getCode(), currentFlow.getCurrentStatus());
            throw new AppException(ResponseCode.E008);
        }

        // AI 通过后进入专家组审核池，等待已配置专家认领。
        int updateResult = approvalFlowMapper.updateApprovalFlowStatus2Expert(
                CopywritingApprovalFlow.builder()
                        .copywritingApprovalFlowId(copywritingApprovalFlowId)
                        .copywritingId(currentFlow.getCopywritingId())
                        .scopeActorId(operatorUserId)
                        .build());
        if (updateResult != 1) {
            log.error("AI审批状态更新失败，影响行数不为1，抛出审批冲突异常");
            throw new AppException(ResponseCode.E008);
        }

        insertApprovalHistory(
                CopywritingApprovalHistory.builder()
                        .operatorId(operatorUserId)
                        .copywritingApprovalFlowId(copywritingApprovalFlowId)
                        .fromStatus(ApprovalStatusEnumVO.AI_PENDING.getCode())
                        .toStatus(ApprovalStatusEnumVO.EXPERT_PENDING.getCode())
                        .operationStatus(ApprovalOperationStatusEnumVO.APPROVE.getCode())
                        .comment(operatedCopywritingApprovalFlowAggregate.getComment())
                        .build()
        );
    }

    @Override
    @Transactional(timeout = 500)
    public void approvalByExpert(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        Long operatorUserId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();
        CopywritingDataScope dataScope = requireActorScope(
                operatedCopywritingApprovalFlowAggregate, ResponseCode.E008);
        boolean globalAccess = dataScope.allAccess();

        log.info("开始专家审批流程，流程ID: {}, 操作人ID: {}", copywritingApprovalFlowId, operatorUserId);

        // 先查询当前流程状态和处理者信息，确保状态匹配（专家阶段）
        CopywritingApprovalFlow currentFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(copywritingApprovalFlowId);
        log.info("查询到当前流程状态: {}, 当前处理者ID: {}", currentFlow != null ? currentFlow.getCurrentStatus() : "null",
                currentFlow != null ? currentFlow.getCurrentProcessorId() : "null");

        if (currentFlow == null) {
            log.error("流程不存在，流程ID: {}", copywritingApprovalFlowId);
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCurrentStatus(), ApprovalStatusEnumVO.EXPERT_PENDING.getCode())) {
            log.error("流程状态不匹配，期望状态: {}, 实际状态: {}",
                    ApprovalStatusEnumVO.EXPERT_PENDING.getCode(), currentFlow.getCurrentStatus());
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCopywritingId(), operatedCopywritingApprovalFlowAggregate.getCopywritingId())) {
            log.error("Expert approval request does not match flow, flowId: {}, requestedCopywritingId: {}, actualCopywritingId: {}",
                    copywritingApprovalFlowId, operatedCopywritingApprovalFlowAggregate.getCopywritingId(), currentFlow.getCopywritingId());
            throw new AppException(ResponseCode.E008);
        }

        if (globalAccess) {
            lockGlobalAdmin(operatorUserId, ResponseCode.E008);
        } else {
            if (!dataScope.expertReviewer()
                    || !Objects.equals(operatorUserId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())) {
                throw new AppException(ResponseCode.E008);
            }
            lockActiveExpertAssignment(
                    currentFlow.getCopywritingId(),
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId(),
                    ResponseCode.E008);
        }

        // Preserve the claimed expert as an optimistic concurrency condition.
        CopywritingApprovalFlow updateFlow = CopywritingApprovalFlow.builder()
                .copywritingApprovalFlowId(copywritingApprovalFlowId)
                .copywritingId(currentFlow.getCopywritingId())
                .currentProcessorId(operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())
                .globalAccess(globalAccess)
                .scopeActorId(operatorUserId)
                .build();

        log.info("执行状态更新，流程ID: {}", copywritingApprovalFlowId);
        int updateResult = approvalFlowMapper.updateApprovalFlowStatus2PlatForm(updateFlow);
        log.info("状态更新结果: 影响行数 = {}", updateResult);

        if (updateResult != 1) {
            log.error("状态更新失败，影响行数不为1，抛出审批冲突异常");
            throw new AppException(ResponseCode.E008);
        } else {
            log.info("状态更新成功，开始插入审批历史记录");
            insertApprovalHistory(
                    CopywritingApprovalHistory.builder()
                            .operatorId(operatedCopywritingApprovalFlowAggregate.getOperatorUserId())
                            .copywritingApprovalFlowId(copywritingApprovalFlowId)
                            .comment(operatedCopywritingApprovalFlowAggregate.getComment())
                            .fromStatus(ApprovalStatusEnumVO.EXPERT_PENDING.getCode())
                            .toStatus(ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())
                            .operationStatus(ApprovalOperationStatusEnumVO.APPROVE.getCode())
                            .build()
            );
        }
    }

    @Override
    @Transactional(timeout = 500)
    public void approvalByPlatform(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        Long operatorUserId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();
        CopywritingDataScope dataScope = requireActorScope(
                operatedCopywritingApprovalFlowAggregate, ResponseCode.E008);
        boolean globalAccess = dataScope.allAccess();

        log.info("开始所属医院/机构管理员终审流程，流程ID: {}, 操作人ID: {}", copywritingApprovalFlowId, operatorUserId);

        // 先查询当前流程状态和处理者信息，确保状态匹配
        CopywritingApprovalFlow currentFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(copywritingApprovalFlowId);
        log.info("查询到当前流程状态: {}, 当前处理者ID: {}", currentFlow != null ? currentFlow.getCurrentStatus() : "null",
                currentFlow != null ? currentFlow.getCurrentProcessorId() : "null");

        if (currentFlow == null) {
            log.error("流程不存在，流程ID: {}", copywritingApprovalFlowId);
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCurrentStatus(), ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())) {
            log.error("流程状态不匹配，期望状态: {}, 实际状态: {}",
                    ApprovalStatusEnumVO.PLATFORM_PENDING.getCode(), currentFlow.getCurrentStatus());
            throw new AppException(ResponseCode.E008);
        }

        if (!Objects.equals(currentFlow.getCopywritingId(), operatedCopywritingApprovalFlowAggregate.getCopywritingId())) {
            throw new AppException(ResponseCode.E008);
        }
        if (globalAccess) {
            lockGlobalAdmin(operatorUserId, ResponseCode.E008);
        } else {
            if (!dataScope.organizationReviewer()
                    || !Objects.equals(operatorUserId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())) {
                throw new AppException(ResponseCode.E008);
            }
            lockActiveOrganizationAdminAssignment(
                    currentFlow.getCopywritingId(),
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId(),
                    ResponseCode.E008);
        }

        // 更新状态，不设置currentProcessorId，让SQL只根据ID和状态进行更新
        CopywritingApprovalFlow updateFlow = CopywritingApprovalFlow.builder()
                .copywritingApprovalFlowId(copywritingApprovalFlowId)
                .copywritingId(currentFlow.getCopywritingId())
                .currentProcessorId(operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())
                .globalAccess(globalAccess)
                .scopeActorId(operatorUserId)
                .build();

        log.info("执行状态更新，流程ID: {}", copywritingApprovalFlowId);
        int updateResult = approvalFlowMapper.updateApprovalFlowStatus2Complete(updateFlow);
        log.info("状态更新结果: 影响行数 = {}", updateResult);

        if (updateResult != 1) {
            log.error("状态更新失败，影响行数不为1，抛出审批冲突异常");
            throw new AppException(ResponseCode.E008);
        } else {
            log.info("状态更新成功，开始插入审批历史记录");
            insertApprovalHistory(
                    CopywritingApprovalHistory.builder()
                            .operatorId(operatedCopywritingApprovalFlowAggregate.getOperatorUserId())
                            .copywritingApprovalFlowId(copywritingApprovalFlowId)
                            .comment(operatedCopywritingApprovalFlowAggregate.getComment())
                            .fromStatus(ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())
                            .toStatus(ApprovalStatusEnumVO.COMPLETE.getCode())
                            .operationStatus(ApprovalOperationStatusEnumVO.APPROVE.getCode())
                            .build()
            );
            if (copywritingMapper.updateStatus2Complete(
                    operatedCopywritingApprovalFlowAggregate.getCopywritingId()) != 1) {
                throw new AppException(ResponseCode.E008);
            }
        }

    }

    @Override
    @Transactional(timeout = 500)
    public void reject(OperatedCopywritingApprovalFlowAggregate operatedCopywritingApprovalFlowAggregate) {
        CopywritingApprovalFlowEntity flowEntity = operatedCopywritingApprovalFlowAggregate.getApprovalFlowEntity();
        Long copywritingApprovalFlowId = flowEntity.getCopywritingApprovalFlowId();
        Long operatorUserId = operatedCopywritingApprovalFlowAggregate.getOperatorUserId();

        log.info("Starting copywriting rejection, flowId: {}, operatorId: {}", copywritingApprovalFlowId, operatorUserId);

        CopywritingApprovalFlow currentFlow = approvalFlowMapper
                .selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(copywritingApprovalFlowId);
        if (currentFlow == null) {
            throw new AppException(ResponseCode.E009);
        }

        Long actualCopywritingId = currentFlow.getCopywritingId();
        if (actualCopywritingId == null
                || !Objects.equals(actualCopywritingId, operatedCopywritingApprovalFlowAggregate.getCopywritingId())) {
            log.error("Reject request does not match approval flow, flowId: {}, requestedCopywritingId: {}, actualCopywritingId: {}",
                    copywritingApprovalFlowId, operatedCopywritingApprovalFlowAggregate.getCopywritingId(), actualCopywritingId);
            throw new AppException(ResponseCode.E009);
        }

        Integer currentStatus = currentFlow.getCurrentStatus();
        if (!Objects.equals(currentStatus, ApprovalStatusEnumVO.AI_PENDING.getCode())
                && !Objects.equals(currentStatus, ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())
                && !Objects.equals(currentStatus, ApprovalStatusEnumVO.EXPERT_PENDING.getCode())
                && !Objects.equals(currentStatus, ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())) {
            throw new AppException(ResponseCode.E009);
        }

        boolean aiRejection = Objects.equals(currentStatus, ApprovalStatusEnumVO.AI_PENDING.getCode());
        CopywritingDataScope dataScope = aiRejection
                ? null
                : requireActorScope(operatedCopywritingApprovalFlowAggregate, ResponseCode.E009);
        boolean globalAccess = dataScope != null && dataScope.allAccess();
        if (aiRejection) {
            if (!Objects.equals(operatorUserId, 0L)
                    || currentFlow.getCurrentProcessorId() != null
                    || operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId() != null) {
                throw new AppException(ResponseCode.E009);
            }
        } else if (globalAccess) {
            lockGlobalAdmin(operatorUserId, ResponseCode.E009);
        } else if (Objects.equals(currentStatus, ApprovalStatusEnumVO.EXPERT_PENDING.getCode())) {
            if (!dataScope.expertReviewer()
                    || !Objects.equals(operatorUserId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())) {
                throw new AppException(ResponseCode.E009);
            }
            lockActiveExpertAssignment(
                    actualCopywritingId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId(),
                    ResponseCode.E009);
        } else if (Objects.equals(currentStatus, ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())) {
            if (!dataScope.organizationReviewer()
                    || !Objects.equals(operatorUserId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())) {
                throw new AppException(ResponseCode.E009);
            }
            lockActiveOrganizationAdminAssignment(
                    actualCopywritingId,
                    operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId(),
                    ResponseCode.E009);
        } else if (Objects.equals(currentStatus, ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())) {
            if (!Objects.equals(operatorUserId, currentFlow.getCurrentProcessorId())
                    || !Objects.equals(operatorUserId, operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())) {
                throw new AppException(ResponseCode.E009);
            }
        }

        CopywritingApprovalFlow updateFlow = CopywritingApprovalFlow.builder()
                .copywritingApprovalFlowId(copywritingApprovalFlowId)
                .copywritingId(actualCopywritingId)
                .currentStatus(currentStatus)
                .currentProcessorId(operatedCopywritingApprovalFlowAggregate.getExpectedProcessorId())
                .globalAccess(globalAccess)
                .scopeActorId(operatorUserId)
                .build();

        int updateResult = approvalFlowMapper.updateApprovalFlowStatus2Rejected(updateFlow);
        if (updateResult != 1) {
            throw new AppException(ResponseCode.E009);
        }

        insertApprovalHistory(
                CopywritingApprovalHistory.builder()
                        .operatorId(operatorUserId)
                        .copywritingApprovalFlowId(copywritingApprovalFlowId)
                        .comment(operatedCopywritingApprovalFlowAggregate.getComment())
                        .fromStatus(currentStatus)
                        .toStatus(ApprovalStatusEnumVO.FAILURE.getCode())
                        .operationStatus(ApprovalOperationStatusEnumVO.REJECT.getCode())
                        .build());
        if (copywritingMapper.updateStatus2Failure(actualCopywritingId) != 1) {
            throw new AppException(ResponseCode.E009);
        }
    }

    @Override
    public CopyWritingApprovalFlowAggregate queryApprovalFlow(Long approvalFlowId) {
        CopywritingApprovalFlow copywritingApprovalFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(approvalFlowId);
        if (copywritingApprovalFlow != null) {
            return CopyWritingApprovalFlowAggregate.builder()
                    .approvalFlowEntity(
                            CopywritingApprovalFlowEntity.builder()
                                    .copywritingApprovalFlowId(copywritingApprovalFlow.getCopywritingApprovalFlowId())
                                    .currentStatus(ApprovalStatusEnumVO.getByCode(copywritingApprovalFlow.getCurrentStatus()))
                                    .createTime(copywritingApprovalFlow.getCreateTime())
                                    .build()
                    )
                    .operatorUserId(copywritingApprovalFlow.getCurrentProcessorId())
                    .build();
        }
        return null;
    }

    @Override
    public void delegateToAssistant(DelegateCommandEntity delegateCommandEntity) {
        // The public contract is denyAll and the current schema has no safe
        // assistant-to-expert assignment relation. Keep the domain path closed
        // as well instead of trusting caller supplied expert/assistant IDs.
        throw new AppException(ResponseCode.FORBIDDEN);
    }

    @Override
    @Transactional(timeout = 500)
    public void claimApprovalTaskByExpert(ClaimTaskEntity claimTaskEntity) {
        CopywritingDataScope dataScope = requireClaimScope(claimTaskEntity);
        boolean globalAccess = dataScope.allAccess();
        log.info("专家：{}领取审批任务", claimTaskEntity.getOperatorId());
        CopywritingApprovalFlow copywritingApprovalFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(claimTaskEntity.getCopywritingApprovalFlowId());
        if (copywritingApprovalFlow == null) {
            throw new AppException(ResponseCode.E010);
        }
        if (copywritingApprovalFlow.getCurrentProcessorId() != null
                || !Objects.equals(copywritingApprovalFlow.getCurrentStatus(), ApprovalStatusEnumVO.EXPERT_PENDING.getCode())) {
            throw new AppException(ResponseCode.E010);
        }
        if (globalAccess) {
            lockGlobalAdmin(claimTaskEntity.getOperatorId(), ResponseCode.E010);
        } else {
            if (!dataScope.expertReviewer()) {
                throw new AppException(ResponseCode.E010);
            }
            lockActiveExpertAssignment(
                    copywritingApprovalFlow.getCopywritingId(),
                    claimTaskEntity.getOperatorId(),
                    ResponseCode.E010);
        }
        int i = approvalFlowMapper.updateApprovalFlowCurrentProcessorIdOnExpert(
                CopywritingApprovalFlow.builder()
                        .currentProcessorId(claimTaskEntity.getOperatorId())
                        .copywritingApprovalFlowId(claimTaskEntity.getCopywritingApprovalFlowId())
                        .globalAccess(globalAccess)
                        .scopeActorId(claimTaskEntity.getOperatorId())
                        .build()
        );
        if (i != 1) {
            throw new AppException(ResponseCode.E010);
        }
        log.info("专家：{}领取审批任务成功", claimTaskEntity.getOperatorId());
    }

    private void lockActiveExpertAssignment(
            Long copywritingId,
            Long expertUserId,
            ResponseCode failureCode) {
        Long activeExpertUserId = copywritingExpertAssignmentMapper.selectActiveExpertUserIdForUpdate(expertUserId);
        if (activeExpertUserId == null) {
            throw new AppException(failureCode);
        }
        Long expertRoleMapId = userRoleMapMapper.selectExpertRoleMapIdForUpdate(expertUserId);
        if (expertRoleMapId == null) {
            throw new AppException(failureCode);
        }
        Long assignmentId = copywritingExpertAssignmentMapper.selectAssignmentIdForUpdate(
                copywritingId,
                expertUserId);
        if (assignmentId == null) {
            throw new AppException(failureCode);
        }
    }

    @Override
    @Transactional(timeout = 500)
    public void claimApprovalTaskByPlatForm(ClaimTaskEntity claimTaskEntity) {
        CopywritingDataScope dataScope = requireClaimScope(claimTaskEntity);
        boolean globalAccess = dataScope.allAccess();
        log.info("所属医院/机构管理员：{}领取终审任务", claimTaskEntity.getOperatorId());
        CopywritingApprovalFlow copywritingApprovalFlow = approvalFlowMapper.selectCopyWritingApprovalFlowByCopyWritingApprovalFlowId(claimTaskEntity.getCopywritingApprovalFlowId());
        if (copywritingApprovalFlow == null) {
            throw new AppException(ResponseCode.E010);
        }
        if (copywritingApprovalFlow.getCurrentProcessorId() != null
                || !Objects.equals(copywritingApprovalFlow.getCurrentStatus(), ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())) {
            throw new AppException(ResponseCode.E010);
        }
        if (globalAccess) {
            lockGlobalAdmin(claimTaskEntity.getOperatorId(), ResponseCode.E010);
        } else {
            if (!dataScope.organizationReviewer()) {
                throw new AppException(ResponseCode.E010);
            }
            lockActiveOrganizationAdminAssignment(
                    copywritingApprovalFlow.getCopywritingId(),
                    claimTaskEntity.getOperatorId(),
                    ResponseCode.E010);
        }
        int i = approvalFlowMapper.updateApprovalFlowCurrentProcessorIdOnPlatForm(
                CopywritingApprovalFlow.builder()
                        .currentProcessorId(claimTaskEntity.getOperatorId())
                        .copywritingApprovalFlowId(claimTaskEntity.getCopywritingApprovalFlowId())
                        .globalAccess(globalAccess)
                        .scopeActorId(claimTaskEntity.getOperatorId())
                        .build()
        );
        if (i != 1) {
            throw new AppException(ResponseCode.E010);
        }
        log.info("所属医院/机构管理员：{}领取终审任务成功", claimTaskEntity.getOperatorId());
    }

    private void lockActiveOrganizationAdminAssignment(
            Long copywritingId,
            Long adminUserId,
            ResponseCode failureCode) {
        Long activeAdminUserId = copywritingOrganizationAdminAssignmentMapper
                .selectActiveAdminUserIdForUpdate(adminUserId);
        if (activeAdminUserId == null) {
            throw new AppException(failureCode);
        }
        Long adminRoleMapId = copywritingOrganizationAdminAssignmentMapper
                .selectOrganizationAdminRoleMapIdForUpdate(adminUserId);
        if (adminRoleMapId == null) {
            throw new AppException(failureCode);
        }
        Long assignmentId = copywritingOrganizationAdminAssignmentMapper
                .selectAssignmentIdForUpdate(copywritingId, adminUserId);
        if (assignmentId == null) {
            throw new AppException(failureCode);
        }
    }

    private CopywritingDataScope requireActorScope(
            OperatedCopywritingApprovalFlowAggregate aggregate,
            ResponseCode failureCode) {
        CopywritingDataScope dataScope = aggregate == null ? null : aggregate.getDataScope();
        if (dataScope == null
                || !Objects.equals(dataScope.actorUserId(), aggregate.getOperatorUserId())) {
            throw new AppException(failureCode);
        }
        return dataScope;
    }

    private CopywritingDataScope requireClaimScope(ClaimTaskEntity claimTaskEntity) {
        CopywritingDataScope dataScope = claimTaskEntity == null ? null : claimTaskEntity.getDataScope();
        if (dataScope == null
                || !Objects.equals(dataScope.actorUserId(), claimTaskEntity.getOperatorId())) {
            throw new AppException(ResponseCode.E010);
        }
        return dataScope;
    }

    private void lockGlobalAdmin(Long actorUserId, ResponseCode failureCode) {
        Long roleMapId = userRoleMapMapper.selectGlobalAdminRoleMapIdForUpdate(actorUserId);
        if (roleMapId == null) {
            throw new AppException(failureCode);
        }
    }

    private void insertApprovalHistory(CopywritingApprovalHistory history) {
        if (approvalHistoryMapper.insert(history) != 1) {
            throw new AppException(ResponseCode.SYSTEM_ERROR);
        }
    }


    @Override
    public OffsetPageResponseData<CopywritingAggregate> queryCopywritingByCopywritingStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            CopywritingStatusEnumVO copywritingStatusEnumVO) {
        log.info("查询用户：{}的文案信息", dataScope.actorUserId());
        long offset = (long) (page - 1) * size;
        Integer copywritingStatus;
        if (copywritingStatusEnumVO == CopywritingStatusEnumVO.PUBLISHED) {
            //审批中 不应该 走这个 函数
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "状态异常");
        }
        if (copywritingStatusEnumVO == null) {
            copywritingStatus = null;
        } else {
            copywritingStatus = copywritingStatusEnumVO.getCode();
        }
        List<Long> ids = copywritingMapper.selectCopywritingIdsPageByScope(
                dataScope.actorUserId(), dataScope.allAccess(), offset, size, copywritingStatus);
        long total = copywritingMapper.countCopywritingsByScope(
                dataScope.actorUserId(), dataScope.allAccess(), copywritingStatus);
        if (ids == null || ids.isEmpty()) {
            return OffsetPageResponseData.<CopywritingAggregate>builder()
                    .data(null)
                    .hasNext(false)
                    .total(total)
                    .page(page)
                    .size(size)
                    .build();
        }
        List<Copywriting> copywritingList = copywritingMapper.selectVisibleCopywritingListByIds(
                ids,
                dataScope.actorUserId(),
                dataScope.allAccess(),
                false,
                false);
        //查询 分类
        List<Long> categoryIds = copywritingList.stream()
                .map(Copywriting::getCopywritingCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<CopywritingCategory> copywritingCategoryList = categoryIds.isEmpty()
                ? List.of()
                : copywritingCategoryMapper.selectCopywritingCategoryListByCopywritingCategoryIds(categoryIds);
        Map<Long, CopywritingCategory> copywritingCategoryMap = copywritingCategoryList.stream()
                .collect(Collectors.toMap(CopywritingCategory::getCopywritingCategoryId, Function.identity()));
        //查询 来源
        List<Long> sourceIds = copywritingList.stream()
                .map(Copywriting::getCopywritingSourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<CopywritingSource> copywritingSourceList = sourceIds.isEmpty()
                ? List.of()
                : copywritingSourceMapper.selectCopywritingSourceListByCopywritingSourceIds(sourceIds);
        Map<Long, CopywritingSource> copywritingSourceMap = copywritingSourceList.stream()
                .collect(Collectors.toMap(CopywritingSource::getCopywritingSourceId, Function.identity()));
        //排序
        Map<Long, Copywriting> copywritingMap = copywritingList.stream()
                .collect(Collectors.toMap(Copywriting::getCopywritingId, Function.identity()));
        //组装
        List<CopywritingAggregate> copywritingAggregateList = ids.stream()
                .filter(copywritingMap::containsKey)
                .map(id -> {
                    Copywriting copywriting = copywritingMap.get(id);
                    CopywritingSource copywritingSource = copywritingSourceMap.get(copywriting.getCopywritingSourceId());
                    CopywritingCategory copywritingCategory = copywritingCategoryMap.get(copywriting.getCopywritingCategoryId());
                    return CopywritingAggregate.builder()
                            .copywritingEntity(
                                    CopywritingEntity.builder()
                                            .copywritingId(copywriting.getCopywritingId())
                                            .title(copywriting.getTitle())
                                            .summary(copywriting.getSummary())
                                            .content(copywriting.getContent())
                                            .status(CopywritingStatusEnumVO.getByCode(copywriting.getStatus()))
                                            .createTime(copywriting.getCreateTime())
                                            .build()
                            )
                            .copywritingCategoryEntity(copywritingCategory == null ? null :
                                    CopywritingCategoryEntity.builder()
                                            .copywritingCategoryId(copywritingCategory.getCopywritingCategoryId())
                                            .name(copywritingCategory.getName())
                                            .build()
                            )
                            .copywritingSourceEntity(copywritingSource == null ? null :
                                    CopywritingSourceEntity.builder()
                                            .copywritingSourceId(copywritingSource.getCopywritingSourceId())
                                            .name(copywritingSource.getName())
                                            .build()
                            )
                            .build();
                }).toList();
        return OffsetPageResponseData.<CopywritingAggregate>builder()
                .data(copywritingAggregateList)
                .hasNext(total > offset + size)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    @Override
    public OffsetPageResponseData<ApprovalCopywritingAggregate> queryCopywritingByApprovalStatus(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            ApprovalStatusEnumVO approvalStatusEnumVO) {
        long offset = (long) (page - 1) * size;
        Integer approvalStatus = approvalStatusEnumVO.getCode();
        List<CopywritingApprovalFlow> approvalFlowList = copywritingApprovalFlowMapper
                .selectVisibleApprovalFlowsPage(
                        dataScope.actorUserId(),
                        dataScope.allAccess(),
                        dataScope.expertReviewer(),
                        dataScope.organizationReviewer(),
                        approvalStatus,
                        offset,
                        size);
        long total = copywritingApprovalFlowMapper.countVisibleApprovalFlows(
                dataScope.actorUserId(),
                dataScope.allAccess(),
                dataScope.expertReviewer(),
                dataScope.organizationReviewer(),
                approvalStatus);

        if (approvalFlowList == null || approvalFlowList.isEmpty()) {
            return OffsetPageResponseData.<ApprovalCopywritingAggregate>builder()
                    .data(null)
                    .hasNext(false)
                    .total(total)
                    .page(page)
                    .size(size)
                    .build();
        }


        //查询文案
        List<Long> copywritingIds = approvalFlowList.stream().map(CopywritingApprovalFlow::getCopywritingId).toList();
        List<Copywriting> copywritingList = copywritingMapper.selectVisibleCopywritingListByIds(
                copywritingIds,
                dataScope.actorUserId(),
                dataScope.allAccess(),
                dataScope.expertReviewer(),
                dataScope.organizationReviewer());
        //排序映射表
        Map<Long, Copywriting> copywritingMap = copywritingList.stream()
                .filter(item -> item.getCopywritingId() != null)
                .collect(Collectors.toMap(Copywriting::getCopywritingId, Function.identity(), (first, ignored) -> first));
        List<CopywritingApprovalFlow> resolvedApprovalFlows = approvalFlowList.stream()
                .filter(flow -> copywritingMap.containsKey(flow.getCopywritingId()))
                .toList();

        List<Long> submitterUserIds = copywritingList.stream()
                .map(Copywriting::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> submitterUserMap = submitterUserIds.isEmpty() ? Map.of() : userMapper.selectUserListByUserIds(submitterUserIds).stream()
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (first, ignored) -> first));

        //查询分类
        List<Long> categoryIds = copywritingList.stream().map(Copywriting::getCopywritingCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CopywritingCategory> copywritingCategoryMap = categoryIds.isEmpty()
                ? Map.of()
                : copywritingCategoryMapper.selectCopywritingCategoryListByCopywritingCategoryIds(categoryIds).stream()
                .filter(category -> category.getCopywritingCategoryId() != null)
                .collect(Collectors.toMap(CopywritingCategory::getCopywritingCategoryId, Function.identity(), (first, ignored) -> first));
        //查询来源
        List<Long> sourceIds = copywritingList.stream().map(Copywriting::getCopywritingSourceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CopywritingSource> copywritingSourceMap = sourceIds.isEmpty()
                ? Map.of()
                : copywritingSourceMapper.selectCopywritingSourceListByCopywritingSourceIds(sourceIds).stream()
                .filter(source -> source.getCopywritingSourceId() != null)
                .collect(Collectors.toMap(CopywritingSource::getCopywritingSourceId, Function.identity(), (first, ignored) -> first));


        //组装
        List<ApprovalCopywritingAggregate> approvalCopywritingAggregateList = resolvedApprovalFlows.stream()
                .map(approvalFlow -> {
                    Copywriting copywriting = copywritingMap.get(approvalFlow.getCopywritingId());
                    CopywritingCategory copywritingCategory = copywritingCategoryMap.get(copywriting.getCopywritingCategoryId());
                    CopywritingSource copywritingSource = copywritingSourceMap.get(copywriting.getCopywritingSourceId());
                    return ApprovalCopywritingAggregate.builder()
                            .copywritingEntity(
                                    CopywritingEntity.builder()
                                            .copywritingId(copywriting.getCopywritingId())
                                            .title(copywriting.getTitle())
                                            .summary(copywriting.getSummary())
                                            .content(copywriting.getContent())
                                            .status(safeCopywritingStatus(copywriting.getStatus()))
                                            .createTime(copywriting.getCreateTime())
                                            .build()
                            )
                            .copywritingApprovalFlowEntity(
                                    CopywritingApprovalFlowEntity.builder()
                                            .copywritingApprovalFlowId(approvalFlow.getCopywritingApprovalFlowId())
                                            .currentStatus(safeApprovalStatus(approvalFlow.getCurrentStatus()))
                                            .createTime(approvalFlow.getCreateTime())
                                            .build()
                            )
                            .copywritingCategoryEntity(copywritingCategory == null ? null :
                                    CopywritingCategoryEntity.builder()
                                            .copywritingCategoryId(copywritingCategory.getCopywritingCategoryId())
                                            .name(copywritingCategory.getName())
                                            .build()
                            )
                            .copywritingSourceEntity(copywritingSource == null ? null :
                                    CopywritingSourceEntity.builder()
                                            .copywritingSourceId(copywritingSource.getCopywritingSourceId())
                                            .name(copywritingSource.getName())
                                            .build()
                            )
                            .submitterUserEntity(toApprovalUserEntity(submitterUserMap.get(copywriting.getUserId())))
                            .build();
                }).toList();

        //查询当前+历史审批人信息可能审批人为空
        List<Long> userIds = resolvedApprovalFlows.stream().map(CopywritingApprovalFlow::getCurrentProcessorId).filter(Objects::nonNull).toList();
        if (!userIds.isEmpty()) {
            List<User> userList = userMapper.selectUserListByUserIds(userIds);
            Map<Long, User> userMap = userList.stream()
                    .filter(user -> user.getUserId() != null)
                    .collect(Collectors.toMap(User::getUserId, Function.identity(), (first, ignored) -> first));
            //组装当审批人
            for (int i = 0; i < approvalCopywritingAggregateList.size(); i++) {
                CopywritingApprovalFlow approvalFlow = resolvedApprovalFlows.get(i);
                Long processorId = approvalFlow.getCurrentProcessorId();
                // 如果当前流程有处理人
                if (processorId != null) {
                    User processor = userMap.get(processorId);
                    // 检查用户是否存在
                    if (processor != null) {
                        ApprovalCopywritingAggregate aggregate = approvalCopywritingAggregateList.get(i);
                        aggregate.setApprovalUserEntity(
                                ApprovalUserEntity.builder()
                                        .userId(processor.getUserId())
                                        .nickName(processor.getNickName())
                                        .avatar(processor.getAvatar())
                                        .genderEnumVO(safeGender(processor.getGender()))
                                        .build()
                        );
                    }
                }
            }
        }


        return OffsetPageResponseData.<ApprovalCopywritingAggregate>builder()
                .data(approvalCopywritingAggregateList)
                .size(size)
                .page(page)
                .total(total)
                .hasNext(total > offset + size)
                .build();
    }

    @Override
    public CopywritingAuditDetailAggregate queryCopywritingAuditDetail(
            Long copywritingId,
            CopywritingDataScope dataScope) {
        Copywriting copywriting = approvalHistoryMapper.selectVisibleCopywritingById(
                copywritingId,
                dataScope.actorUserId(),
                dataScope.allAccess(),
                dataScope.expertReviewer(),
                dataScope.organizationReviewer());
        if (copywriting == null) {
            throw new AppException(ResponseCode.FORBIDDEN);
        }

        CopywritingApprovalFlow approvalFlow = approvalFlowMapper.selectLatestByCopywritingId(copywritingId);
        CopywritingCategory category = copywriting.getCopywritingCategoryId() == null
                ? null
                : copywritingCategoryMapper.selectById(copywriting.getCopywritingCategoryId());
        CopywritingSource source = copywriting.getCopywritingSourceId() == null
                ? null
                : copywritingSourceMapper.selectById(copywriting.getCopywritingSourceId());
        User submitter = copywriting.getUserId() == null
                ? null
                : userMapper.selectUserByUserId(copywriting.getUserId());
        User processor = approvalFlow == null || approvalFlow.getCurrentProcessorId() == null
                ? null
                : userMapper.selectUserByUserId(approvalFlow.getCurrentProcessorId());

        boolean assignedExpert = dataScope.allAccess() || (dataScope.expertReviewer()
                && approvalHistoryMapper.countExpertAssignmentForCopywriting(
                copywritingId, dataScope.actorUserId()) > 0);
        boolean assignedOrganization = dataScope.allAccess() || (dataScope.organizationReviewer()
                && approvalHistoryMapper.countOrganizationAssignmentForCopywriting(
                copywritingId, dataScope.actorUserId()) > 0);

        ApprovalCopywritingAggregate copywritingAggregate = ApprovalCopywritingAggregate.builder()
                .copywritingEntity(CopywritingEntity.builder()
                        .copywritingId(copywriting.getCopywritingId())
                        .title(copywriting.getTitle())
                        .summary(copywriting.getSummary())
                        .content(copywriting.getContent())
                        .status(safeCopywritingStatus(copywriting.getStatus()))
                        .createTime(copywriting.getCreateTime())
                        .build())
                .copywritingCategoryEntity(category == null ? null : CopywritingCategoryEntity.builder()
                        .copywritingCategoryId(category.getCopywritingCategoryId())
                        .name(category.getName())
                        .build())
                .copywritingSourceEntity(source == null ? null : CopywritingSourceEntity.builder()
                        .copywritingSourceId(source.getCopywritingSourceId())
                        .name(source.getName())
                        .build())
                .copywritingApprovalFlowEntity(approvalFlow == null ? null : CopywritingApprovalFlowEntity.builder()
                        .copywritingApprovalFlowId(approvalFlow.getCopywritingApprovalFlowId())
                        .currentStatus(safeApprovalStatus(approvalFlow.getCurrentStatus()))
                        .createTime(approvalFlow.getCreateTime())
                        .build())
                .approvalUserEntity(toApprovalUserEntity(processor))
                .submitterUserEntity(toApprovalUserEntity(submitter))
                .expertReviewer(assignedExpert)
                .organizationReviewer(assignedOrganization)
                .build();

        List<CopywritingAuditRecordEntity> history = approvalHistoryMapper
                .selectVisibleAuditRecordsByCopywritingId(
                        copywritingId,
                        dataScope.actorUserId(),
                        dataScope.allAccess(),
                        dataScope.expertReviewer(),
                        dataScope.organizationReviewer())
                .stream()
                .map(this::toAuditRecordEntity)
                .toList();

        return CopywritingAuditDetailAggregate.builder()
                .copywriting(copywritingAggregate)
                .auditHistory(history)
                .build();
    }

    @Override
    public OffsetPageResponseData<CopywritingAuditRecordEntity> queryVisibleAuditRecords(
            CopywritingDataScope dataScope,
            Integer page,
            Integer size,
            Long copywritingId,
            Integer auditorStage,
            Boolean approved) {
        long total = approvalHistoryMapper.countVisibleAuditRecords(
                dataScope.actorUserId(),
                dataScope.allAccess(),
                dataScope.expertReviewer(),
                dataScope.organizationReviewer(),
                copywritingId,
                auditorStage,
                approved);
        long offset = (long) (page - 1) * size;
        List<CopywritingAuditRecordEntity> records = total == 0
                ? List.of()
                : approvalHistoryMapper.selectVisibleAuditRecords(
                                dataScope.actorUserId(),
                                dataScope.allAccess(),
                                dataScope.expertReviewer(),
                                dataScope.organizationReviewer(),
                                copywritingId,
                                auditorStage,
                                approved,
                                offset,
                                size)
                        .stream()
                        .map(this::toAuditRecordEntity)
                        .toList();
        return OffsetPageResponseData.<CopywritingAuditRecordEntity>builder()
                .data(records)
                .hasNext(total > offset + records.size())
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    private CopywritingAuditRecordEntity toAuditRecordEntity(CopywritingAuditRecordView record) {
        Long historyId = record.getCopywritingApprovalHistoryId() == null
                ? record.getHistoryDatabaseId()
                : record.getCopywritingApprovalHistoryId();
        return CopywritingAuditRecordEntity.builder()
                .copywritingApprovalHistoryId(historyId)
                .copywritingApprovalFlowId(record.getCopywritingApprovalFlowId())
                .copywritingId(record.getCopywritingId())
                .copywritingTitle(record.getCopywritingTitle())
                .operatorId(record.getOperatorId())
                .operatorName(record.getOperatorName())
                .approved(record.getOperationStatus())
                .fromStatus(record.getFromStatus())
                .toStatus(record.getToStatus())
                .comment(record.getComment())
                .operationTime(record.getCreateTime())
                .build();
    }

    private CopywritingStatusEnumVO safeCopywritingStatus(Integer status) {
        if (status == null) {
            return null;
        }
        try {
            return CopywritingStatusEnumVO.getByCode(status);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ApprovalStatusEnumVO safeApprovalStatus(Integer status) {
        if (status == null) {
            return null;
        }
        try {
            return ApprovalStatusEnumVO.getByCode(status);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ApprovalUserEntity toApprovalUserEntity(User user) {
        if (user == null) {
            return null;
        }
        return ApprovalUserEntity.builder()
                .userId(user.getUserId())
                .nickName(user.getNickName())
                .avatar(user.getAvatar())
                .genderEnumVO(safeGender(user.getGender()))
                .build();
    }

    private GenderEnumVO safeGender(Integer gender) {
        if (gender == null) {
            return GenderEnumVO.UNKNOWN;
        }
        try {
            return GenderEnumVO.fromCode(gender);
        } catch (RuntimeException ignored) {
            return GenderEnumVO.UNKNOWN;
        }
    }

    @Override
    public List<CopywritingCategoryEntity> queryAllValidCopywritingCategory() {
        List<CopywritingCategory> copywritingCategoryList = copywritingCategoryMapper.selectAllValidCopywritingCategory();
        if (copywritingCategoryList == null) {
            return null;
        }
        return copywritingCategoryList.stream()
                .map(copywritingCategory ->
                        CopywritingCategoryEntity.builder()
                                .copywritingCategoryId(copywritingCategory.getCopywritingCategoryId())
                                .name(copywritingCategory.getName())
                                .build()
                ).toList();
    }

    @Override
    public List<CopywritingSourceEntity> queryAllValidCopywritingSource() {
        List<CopywritingSource> copywritingSourceList = copywritingSourceMapper.selectAllValidCopywritingSource();
        if (copywritingSourceList == null) {
            return null;
        }
        return copywritingSourceList.stream()
                .map(copywritingSource ->
                        CopywritingSourceEntity.builder()
                                .copywritingSourceId(copywritingSource.getCopywritingSourceId())
                                .name(copywritingSource.getName())
                                .build()
                ).toList();
    }

    @Override
    public void createCopywritingCategory(String name) {
        if (copywritingCategoryMapper.insert(
                CopywritingCategory.builder()
                        .name(name)
                        .build()
        ) != 1) {
            throw new AppException(ResponseCode.SYSTEM_ERROR);
        }
    }

    @Override
    public void createCopywritingSource(String name) {
        if (copywritingSourceMapper.insert(
                CopywritingSource.builder()
                        .name(name)
                        .build()
        ) != 1) {
            throw new AppException(ResponseCode.SYSTEM_ERROR);
        }
    }


}
