package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.ICopywritingApi;
import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiOffsetPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.copywriting.model.aggregate.ApprovalCopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAuditDetailAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CopywritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.CreateCopyWritingAggregate;
import com.hzmwmt.domain.copywriting.model.aggregate.OperatedCopywritingApprovalFlowAggregate;
import com.hzmwmt.domain.copywriting.model.entity.*;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import com.hzmwmt.domain.copywriting.service.ICopyWritingApprovalService;
import com.hzmwmt.domain.copywriting.service.ICopywritingManagerService;
import com.hzmwmt.trigger.job.CopywritingAiApprovalJob;
import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.RoleCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingController
 * @Date: 2025/9/10 14:16
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/copywriting/")
public class CopywritingController implements ICopywritingApi {

    @Resource
    private ICopyWritingApprovalService copyWritingApprovalService;

    @Resource
    private ICopywritingManagerService copywritingManagerService;

    @Resource
    private IAuthService authService;

    @Resource
    private CopywritingAiApprovalJob copywritingAiApprovalJob;


    @Override
    @RequestMapping(value = "list/by-status", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "按文案状态查询文案列表", recordParams = true)
    public Response<ApiOffsetPageData<CopywritingResponseDTO>> queryCopywritingByCopywritingStatus(
            @RequestParam(name = "page", defaultValue = "1")
            Integer page,
            @RequestParam(name = "size", defaultValue = "10")
            Integer size,
            @RequestParam(name = "status")
            Integer copywritingStatus) {

        log.info("查询文案列表(按文案状态), page: {}, size: {}, status code: {}", page, size, copywritingStatus);

        CopywritingDataScope dataScope = currentDataScope();
        CopywritingStatusEnumVO statusEnum;
        if (copywritingStatus == null) {
            statusEnum = null;
        } else {
            statusEnum = CopywritingStatusEnumVO.getByCode(copywritingStatus);
        }
        OffsetPageResponseData<CopywritingAggregate> domainPage = copywritingManagerService
                .queryCopywritingByCopywritingStatus(dataScope, page, size, statusEnum);

        if (domainPage.getData() == null) {
            return Response.<ApiOffsetPageData<CopywritingResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(
                            ApiOffsetPageData.<CopywritingResponseDTO>builder()
                                    .hasNext(false)
                                    .total(domainPage.getTotal())
                                    .size(0)
                                    .page(domainPage.getPage())
                                    .data(null)
                                    .build()
                    ).build();
        }

        List<CopywritingResponseDTO> dtoList = domainPage.getData().stream().map(agg -> {
            CopywritingEntity entity = agg.getCopywritingEntity();
            CopywritingCategoryEntity categoryEntity = agg.getCopywritingCategoryEntity();
            CopywritingSourceEntity sourceEntity = agg.getCopywritingSourceEntity();

            CopywritingResponseDTO.CopywritingDTO copywritingDTO = CopywritingResponseDTO.CopywritingDTO.builder()
                    .copywritingId(entity.getCopywritingId())
                    .title(entity.getTitle())
                    .summary(entity.getSummary())
                    .status(entity.getStatus().getDesc())
                    .createTime(entity.getCreateTime())
                    .build();

            return CopywritingResponseDTO.builder()
                    .copywriting(copywritingDTO)
                    .sourceName(sourceEntity.getName())
                    .categoryName(categoryEntity.getName())
                    .build();
        }).collect(Collectors.toList());

        ApiOffsetPageData<CopywritingResponseDTO> responsePage = ApiOffsetPageData.<CopywritingResponseDTO>builder()
                .data(dtoList)
                .hasNext(domainPage.getHasNext())
                .total(domainPage.getTotal())
                .page(domainPage.getPage())
                .size(domainPage.getSize())
                .build();

        return Response.<ApiOffsetPageData<CopywritingResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    @Override
    @RequestMapping(value = "list/by-approval-status", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "按审核状态查询文案列表", recordParams = true)
    public Response<ApiOffsetPageData<ApprovalCopywritingResponseDTO>> queryCopywritingByApprovalStatus(
            @RequestParam(name = "page")
            Integer page,
            @RequestParam(name = "size")
            Integer size,
            @RequestParam(name = "status")
            Integer approvalStatus) {

        log.info("查询文案列表(按审批状态), page: {}, size: {}, status code: {}", page, size, approvalStatus);

        try {
            // 从token获取用户信息
            AuthUserEntity authUserEntity = authService.queryValidatedUser();
            Long userId = authUserEntity.getUserId();
            log.info("用户认证成功，用户ID: {}, 开始查询审批状态为{}的文案数据", userId, approvalStatus);

            // 验证并转换审批状态
            ApprovalStatusEnumVO statusEnum = ApprovalStatusEnumVO.getByCode(approvalStatus);
            if (statusEnum == null) {
                log.warn("用户ID: {} 传入了无效的审批状态码: {}", userId, approvalStatus);
                return Response.<ApiOffsetPageData<ApprovalCopywritingResponseDTO>>builder()
                        .code(ResponseCode.PARAM_ERROR.getCode())
                        .info("无效的审批状态码")
                        .data(null)
                        .build();
            }

            // 调用服务层进行查询
            CopywritingDataScope dataScope = dataScopeFor(authUserEntity);
            boolean reviewExpert = dataScope.canReviewExpertStage();
            boolean reviewOrganization = dataScope.canReviewOrganizationStage();
            OffsetPageResponseData<ApprovalCopywritingAggregate> domainPage = copywritingManagerService
                    .queryCopywritingByApprovalStatus(dataScope, page, size, statusEnum);
            log.info("用户ID: {} 查询成功，获取到{}条数据", userId, domainPage.getTotal());

            if (domainPage.getData() == null) {
                return Response.<ApiOffsetPageData<ApprovalCopywritingResponseDTO>>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(
                                ApiOffsetPageData.<ApprovalCopywritingResponseDTO>builder()
                                        .hasNext(false)
                                        .total(domainPage.getTotal())
                                        .size(0)
                                        .page(domainPage.getPage())
                                        .data(null)
                                        .build()
                        ).build();
            }
            List<ApprovalCopywritingResponseDTO> dtoList = domainPage.getData().stream().map(agg -> {
                CopywritingEntity copywritingEntity = agg.getCopywritingEntity();
                CopywritingCategoryEntity categoryEntity = agg.getCopywritingCategoryEntity();
                CopywritingSourceEntity sourceEntity = agg.getCopywritingSourceEntity();
                CopywritingApprovalFlowEntity flowEntity = agg.getCopywritingApprovalFlowEntity();
                ApprovalUserEntity userEntity = agg.getApprovalUserEntity();
                ApprovalUserEntity submitterEntity = agg.getSubmitterUserEntity();

                ApprovalCopywritingResponseDTO.CopywritingDTO copywritingDTO = ApprovalCopywritingResponseDTO.CopywritingDTO.builder()
                        .copywritingId(copywritingEntity.getCopywritingId())
                        .title(copywritingEntity.getTitle())
                        .summary(copywritingEntity.getSummary())
                        .content(copywritingEntity.getContent())
                        .status(copywritingEntity.getStatus() == null ? "未知状态" : copywritingEntity.getStatus().getDesc())
                        .createTime(copywritingEntity.getCreateTime())
                        .build();


                ApprovalCopywritingResponseDTO.CopywritingApprovalFlowDTO flowDTO = ApprovalCopywritingResponseDTO.CopywritingApprovalFlowDTO.builder()
                        .copywritingApprovalFlowId(flowEntity.getCopywritingApprovalFlowId())
                        .currentStatus(flowEntity.getCurrentStatus() == null ? "未知状态" : flowEntity.getCurrentStatus().getDesc())
                        .currentStatusCode(flowEntity.getCurrentStatus() == null ? null : flowEntity.getCurrentStatus().getCode())
                        .createTime(flowEntity.getCreateTime())
                        .build();
                ApprovalCopywritingResponseDTO.ApprovalUserDTO userDTO = null;
                if (userEntity != null) {
                    userDTO = ApprovalCopywritingResponseDTO.ApprovalUserDTO.builder()
                            .userId(userEntity.getUserId())
                            .nickName(userEntity.getNickName())
                            .avatar(userEntity.getAvatar())
                            .gender(userEntity.getGenderEnumVO() == null ? null : userEntity.getGenderEnumVO().getDescription())
                            .build();

                }

                ApprovalCopywritingResponseDTO.ApprovalUserDTO submitterDTO = null;
                if (submitterEntity != null) {
                    submitterDTO = ApprovalCopywritingResponseDTO.ApprovalUserDTO.builder()
                            .userId(submitterEntity.getUserId())
                            .nickName(submitterEntity.getNickName())
                            .avatar(submitterEntity.getAvatar())
                            .gender(submitterEntity.getGenderEnumVO() == null ? null : submitterEntity.getGenderEnumVO().getDescription())
                            .build();
                }

                return ApprovalCopywritingResponseDTO.builder()
                        .copywriting(copywritingDTO)
                        .sourceName(sourceEntity == null || sourceEntity.getName() == null ? "未设置来源" : sourceEntity.getName())
                        .categoryName(categoryEntity == null || categoryEntity.getName() == null ? "未设置分类" : categoryEntity.getName())
                        .approvalFlow(flowDTO)
                        .approvalUser(userDTO)
                        .submitterUser(submitterDTO)
                        .expertReviewer(reviewExpert)
                        .organizationReviewer(
                                reviewOrganization
                                        && flowEntity.getCurrentStatus() == ApprovalStatusEnumVO.PLATFORM_PENDING)
                        .build();
            }).collect(Collectors.toList());

            ApiOffsetPageData<ApprovalCopywritingResponseDTO> responsePage = new ApiOffsetPageData<>(dtoList, domainPage.getHasNext(), domainPage.getTotal(), domainPage.getPage(), domainPage.getSize());

            return Response.<ApiOffsetPageData<ApprovalCopywritingResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responsePage)
                    .build();
        } catch (IllegalStateException e) {
            log.error("用户未认证或认证已过期，异常类型: {}", e.getClass().getSimpleName());
            return Response.<ApiOffsetPageData<ApprovalCopywritingResponseDTO>>builder()
                    .code(ResponseCode.UNAUTHORIZED.getCode())
                    .info("用户未认证，请先登录")
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("查询文案列表(按审批状态)时发生异常，异常类型: {}", e.getClass().getSimpleName());
            return Response.<ApiOffsetPageData<ApprovalCopywritingResponseDTO>>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info("系统异常，请稍后重试")
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("audit-records")
    public Response<ApiOffsetPageData<CopywritingAuditRecordResponseDTO>> queryAuditRecords(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "copywritingId", required = false) Long copywritingId,
            @RequestParam(name = "auditorRole", required = false) String auditorRole,
            @RequestParam(name = "auditResult", required = false) String auditResult) {
        CopywritingDataScope dataScope = currentDataScope();

        OffsetPageResponseData<CopywritingAuditRecordEntity> domainPage =
                copywritingManagerService.queryVisibleAuditRecords(
                        dataScope,
                        page,
                        size,
                        copywritingId,
                        parseAuditorStage(auditorRole),
                        parseAuditResult(auditResult));
        List<CopywritingAuditRecordResponseDTO> records = domainPage.getData() == null
                ? List.of()
                : domainPage.getData().stream().map(this::toAuditRecordDTO).toList();
        ApiOffsetPageData<CopywritingAuditRecordResponseDTO> data =
                ApiOffsetPageData.<CopywritingAuditRecordResponseDTO>builder()
                        .data(records)
                        .hasNext(domainPage.getHasNext())
                        .total(domainPage.getTotal())
                        .page(domainPage.getPage())
                        .size(domainPage.getSize())
                        .build();
        return success(data);
    }

    @Override
    @GetMapping("{copywritingId}/detail")
    public Response<CopywritingAuditDetailResponseDTO> queryCopywritingAuditDetail(
            @PathVariable("copywritingId") Long copywritingId) {
        return success(toAuditDetailDTO(loadVisibleAuditDetail(copywritingId)));
    }

    @Override
    @GetMapping("{copywritingId}/audit-history")
    public Response<List<CopywritingAuditRecordResponseDTO>> queryCopywritingAuditHistory(
            @PathVariable("copywritingId") Long copywritingId) {
        CopywritingAuditDetailAggregate detail = loadVisibleAuditDetail(copywritingId);
        List<CopywritingAuditRecordResponseDTO> history = detail.getAuditHistory() == null
                ? List.of()
                : detail.getAuditHistory().stream().map(this::toAuditRecordDTO).toList();
        return success(history);
    }

    private CopywritingAuditDetailAggregate loadVisibleAuditDetail(Long copywritingId) {
        return copywritingManagerService.queryCopywritingAuditDetail(
                copywritingId,
                currentDataScope());
    }

    private CopywritingAuditDetailResponseDTO toAuditDetailDTO(CopywritingAuditDetailAggregate detail) {
        ApprovalCopywritingAggregate aggregate = detail.getCopywriting();
        CopywritingEntity copywriting = aggregate.getCopywritingEntity();
        CopywritingCategoryEntity category = aggregate.getCopywritingCategoryEntity();
        CopywritingSourceEntity source = aggregate.getCopywritingSourceEntity();
        CopywritingApprovalFlowEntity flow = aggregate.getCopywritingApprovalFlowEntity();
        return CopywritingAuditDetailResponseDTO.builder()
                .copywriting(ApprovalCopywritingResponseDTO.CopywritingDTO.builder()
                        .copywritingId(copywriting.getCopywritingId())
                        .title(copywriting.getTitle())
                        .summary(copywriting.getSummary())
                        .content(copywriting.getContent())
                        .status(copywriting.getStatus() == null ? "未知状态" : copywriting.getStatus().getDesc())
                        .createTime(copywriting.getCreateTime())
                        .build())
                .categoryName(category == null || category.getName() == null ? "未设置分类" : category.getName())
                .sourceName(source == null || source.getName() == null ? "未设置来源" : source.getName())
                .approvalFlow(flow == null ? null : ApprovalCopywritingResponseDTO.CopywritingApprovalFlowDTO.builder()
                        .copywritingApprovalFlowId(flow.getCopywritingApprovalFlowId())
                        .currentStatus(flow.getCurrentStatus() == null ? "未知状态" : flow.getCurrentStatus().getDesc())
                        .currentStatusCode(flow.getCurrentStatus() == null ? null : flow.getCurrentStatus().getCode())
                        .createTime(flow.getCreateTime())
                        .build())
                .approvalUser(toApprovalUserDTO(aggregate.getApprovalUserEntity()))
                .submitterUser(toApprovalUserDTO(aggregate.getSubmitterUserEntity()))
                .expertReviewer(Boolean.TRUE.equals(aggregate.getExpertReviewer()))
                .organizationReviewer(Boolean.TRUE.equals(aggregate.getOrganizationReviewer()))
                .auditHistory(detail.getAuditHistory() == null
                        ? List.of()
                        : detail.getAuditHistory().stream().map(this::toAuditRecordDTO).toList())
                .build();
    }

    private ApprovalCopywritingResponseDTO.ApprovalUserDTO toApprovalUserDTO(ApprovalUserEntity user) {
        if (user == null) {
            return null;
        }
        return ApprovalCopywritingResponseDTO.ApprovalUserDTO.builder()
                .userId(user.getUserId())
                .nickName(user.getNickName())
                .avatar(user.getAvatar())
                .gender(user.getGenderEnumVO() == null ? null : user.getGenderEnumVO().getDescription())
                .build();
    }

    private CopywritingAuditRecordResponseDTO toAuditRecordDTO(CopywritingAuditRecordEntity history) {
        String role = Long.valueOf(0L).equals(history.getOperatorId())
                ? "AI"
                : auditRole(history.getFromStatus());
        String operatorName = auditOperatorName(history);
        String result = history.getApproved() == null ? "未知" : history.getApproved() ? "通过" : "驳回";
        String fromStatus = approvalStatusDescription(history.getFromStatus());
        String toStatus = approvalStatusDescription(history.getToStatus());
        return CopywritingAuditRecordResponseDTO.builder()
                .id(history.getCopywritingApprovalHistoryId())
                .copywritingId(history.getCopywritingId())
                .copywritingApprovalFlowId(history.getCopywritingApprovalFlowId())
                .copywritingTitle(history.getCopywritingTitle() == null ? "未知文案" : history.getCopywritingTitle())
                .operatorId(history.getOperatorId())
                .operationTime(history.getOperationTime())
                .operatorName(operatorName)
                .operatorRole(role)
                .operation(result)
                .comment(history.getComment())
                .fromStatus(fromStatus)
                .fromStatusCode(history.getFromStatus())
                .toStatus(toStatus)
                .toStatusCode(history.getToStatus())
                .auditTime(history.getOperationTime())
                .auditorName(operatorName)
                .auditorRole(role)
                .auditResult(result)
                .auditComment(history.getComment())
                .previousStatus(fromStatus)
                .currentStatus(toStatus)
                .build();
    }

    private String auditOperatorName(CopywritingAuditRecordEntity history) {
        if (Integer.valueOf(ApprovalStatusEnumVO.AI_PENDING.getCode()).equals(history.getFromStatus())
                || Long.valueOf(0L).equals(history.getOperatorId())) {
            return "AI 自动审核";
        }
        String name = history.getOperatorName();
        return name == null || name.isBlank() ? "未知处理人" : name;
    }

    private String auditRole(Integer fromStatus) {
        if (fromStatus == null) {
            return "未知角色";
        }
        if (fromStatus.equals(ApprovalStatusEnumVO.DOCTOR_PENDING.getCode())) return "作者";
        if (fromStatus.equals(ApprovalStatusEnumVO.AI_PENDING.getCode())) return "AI";
        if (fromStatus.equals(ApprovalStatusEnumVO.EXPERT_PENDING.getCode())) return "专家组";
        if (fromStatus.equals(ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())) return "机构管理员";
        return "未知角色";
    }

    private String approvalStatusDescription(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        try {
            return ApprovalStatusEnumVO.getByCode(status).getDesc();
        } catch (RuntimeException ignored) {
            return "未知状态(" + status + ")";
        }
    }

    private Integer parseAuditorStage(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "作者", "AUTHOR", "DOCTOR" -> ApprovalStatusEnumVO.DOCTOR_PENDING.getCode();
            case "AI", "AI自动审核" -> ApprovalStatusEnumVO.AI_PENDING.getCode();
            case "专家", "专家组", "EXPERT" -> ApprovalStatusEnumVO.EXPERT_PENDING.getCode();
            case "机构管理员", "医院/机构管理员", "ORG_ADMIN", "ORGANIZATION" ->
                    ApprovalStatusEnumVO.PLATFORM_PENDING.getCode();
            default -> throw new com.hzmwmt.types.exception.AppException(
                    ResponseCode.PARAM_ERROR.getCode(), "未知的审核角色筛选值");
        };
    }

    private Boolean parseAuditResult(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "通过", "批准", "APPROVE", "APPROVED", "TRUE" -> Boolean.TRUE;
            case "驳回", "REJECT", "REJECTED", "FALSE" -> Boolean.FALSE;
            default -> throw new com.hzmwmt.types.exception.AppException(
                    ResponseCode.PARAM_ERROR.getCode(), "未知的审核结果筛选值");
        };
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }


    @Override
    @RequestMapping(value = "categories/all", method = RequestMethod.GET)
    public Response<List<CopywritingCategoryResponseDTO>> queryAllValidCopywritingCategory() {
        log.info("查询所有有效文案分类");
        List<CopywritingCategoryEntity> entities = copywritingManagerService.queryAllValidCopywritingCategory();
        if (entities == null || entities.isEmpty()) {
            return Response.<List<CopywritingCategoryResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(null)
                    .build();
        }
        List<CopywritingCategoryResponseDTO> dtoList = entities.stream()
                .map(entity -> CopywritingCategoryResponseDTO.builder()
                        .copywritingCategoryId(entity.getCopywritingCategoryId())
                        .name(entity.getName())
                        .build())
                .collect(Collectors.toList());

        return Response.<List<CopywritingCategoryResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(dtoList)
                .build();
    }

    @Override
    @RequestMapping(value = "sources/all", method = RequestMethod.GET)
    public Response<List<CopywritingSourceResponseDTO>> queryAllValidCopywritingSource() {
        log.info("查询所有有效文案来源");
        List<CopywritingSourceEntity> entities = copywritingManagerService.queryAllValidCopywritingSource();
        if (entities == null || entities.isEmpty()) {
            Response.<List<CopywritingSourceResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(null)
                    .build();
        }
        List<CopywritingSourceResponseDTO> dtoList = entities.stream()
                .map(entity -> CopywritingSourceResponseDTO.builder()
                        .copywritingSourceId(entity.getCopywritingSourceId())
                        .name(entity.getName())
                        .build())
                .collect(Collectors.toList());

        return Response.<List<CopywritingSourceResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(dtoList)
                .build();
    }


    @Override
    @RequestMapping(value = "submit", method = RequestMethod.POST)
    @Idempotent
    @OperationLog(operationType = "提交", description = "提交文案", recordParams = true)
    public Response<Void> submit(@RequestBody SubmitCopywritingRequestDTO requestDTO) {
        log.info("开始提交文案，标题长度: {}",
                requestDTO.getTitle() == null ? 0 : requestDTO.getTitle().length());
        Long userId = authService.queryValidatedUser().getUserId();
        copyWritingApprovalService.submit(
                CreateCopyWritingAggregate.builder()
                        .copywritingEntity(
                                CopywritingEntity.builder()
                                        .title(requestDTO.getTitle())
                                        .summary(requestDTO.getSummary())
                                        .content(requestDTO.getContent())
                                        .build()
                        )
                        .copywritingCategoryId(requestDTO.getCopywritingCategoryId())
                        .copywritingSourceId(requestDTO.getCopywritingSourceId())
                        .userId(userId)
                        .build());

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = {"confirm/by-author", "approve/by-doctor"}, method = RequestMethod.POST)
    @OperationLog(operationType = "确认", description = "作者自审并确认文案", recordParams = true)
    public Response<Void> approvalByDoctor(@RequestBody ApprovalRequestDTO requestDTO) {
        log.info("作者自审确认, 文案ID: {}", requestDTO.getCopywritingId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        CopywritingApprovalFlowEntity flowEntity = CopywritingApprovalFlowEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .build();

        OperatedCopywritingApprovalFlowAggregate aggregate = OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(flowEntity)
                .copywritingId(requestDTO.getCopywritingId())
                .comment(requestDTO.getComment())
                .operatorUserId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.approvalByDoctor(aggregate);
        copywritingAiApprovalJob.triggerApprovalAsync(requestDTO.getCopywritingId());

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "approve/by-ai", method = RequestMethod.POST)
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(operationType = "审核", description = "AI 审核文案（手动触发）")
    public Response<Void> approvalByAI(long copywritingId) {
        CopywritingDataScope dataScope = currentDataScope();
        if (!dataScope.allAccess()) {
            throw new com.hzmwmt.types.exception.AppException(ResponseCode.FORBIDDEN);
        }
        // The detail query performs a database-backed active ADMIN/SUPER_ADMIN
        // recheck, preventing a stale privileged token from triggering review.
        copywritingManagerService.queryCopywritingAuditDetail(copywritingId, dataScope);
        copywritingAiApprovalJob.triggerApproval(copywritingId);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "approve/by-expert", method = RequestMethod.POST)
    public Response<Void> approvalByExpert(@RequestBody ApprovalRequestDTO requestDTO) {
        log.info("专家审批, 文案ID: {}", requestDTO.getCopywritingId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        CopywritingApprovalFlowEntity flowEntity = CopywritingApprovalFlowEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .build();

        OperatedCopywritingApprovalFlowAggregate aggregate = OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(flowEntity)
                .copywritingId(requestDTO.getCopywritingId())
                .comment(requestDTO.getComment())
                .operatorUserId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.approvalByExpert(aggregate);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "approve/by-platform", method = RequestMethod.POST)
    @OperationLog(operationType = "审核", description = "所属医院/机构管理员终审文案", recordParams = true)
    public Response<Void> approvalByPlatform(@RequestBody ApprovalRequestDTO requestDTO) {
        log.info("所属医院/机构管理员终审, 文案ID: {}", requestDTO.getCopywritingId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        CopywritingApprovalFlowEntity flowEntity = CopywritingApprovalFlowEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .build();

        OperatedCopywritingApprovalFlowAggregate aggregate = OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(flowEntity)
                .copywritingId(requestDTO.getCopywritingId())
                .comment(requestDTO.getComment())
                .operatorUserId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.approvalByPlatform(aggregate);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "reject", method = RequestMethod.POST)
    public Response<Void> reject(@RequestBody ApprovalRequestDTO requestDTO) {
        log.info("驳回审批, 文案ID: {}", requestDTO.getCopywritingId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        CopywritingApprovalFlowEntity flowEntity = CopywritingApprovalFlowEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .build();

        OperatedCopywritingApprovalFlowAggregate aggregate = OperatedCopywritingApprovalFlowAggregate.builder()
                .approvalFlowEntity(flowEntity)
                .copywritingId(requestDTO.getCopywritingId())
                .comment(requestDTO.getComment())
                .operatorUserId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.reject(aggregate);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "create-category", method = RequestMethod.POST)
    public Response<Void> createCopywritingCategory(@RequestParam String name) {
        copywritingManagerService.createCopywritingCategory(name);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "create-source", method = RequestMethod.POST)
    public Response<Void> createCopywritingSource(@RequestParam String name) {
        copywritingManagerService.createCopywritingSource(name);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "delegate", method = RequestMethod.POST)
    @PreAuthorize("denyAll()")
    public Response<Void> delegateToAssistant(@RequestBody DelegateToAssistantRequestDTO requestDTO) {
        log.info("指派任务, 流程ID: {}, 助理ID: {}", requestDTO.getApprovalFlowId(), requestDTO.getAssistantId());
        Long expertId = authService.queryValidatedUser().getUserId();

        DelegateCommandEntity command = DelegateCommandEntity.builder()
                .approvalFlowId(requestDTO.getApprovalFlowId())
                .assistantId(requestDTO.getAssistantId())
                .expertId(expertId)
                .build();

        copyWritingApprovalService.delegateToAssistant(command);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "claim/by-expert", method = RequestMethod.POST)
    public Response<Void> claimApprovalTaskByExpert(@RequestBody ClaimTaskRequestDTO requestDTO) {
        log.info("专家领取任务, 流程ID: {}", requestDTO.getCopywritingApprovalFlowId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        ClaimTaskEntity entity = ClaimTaskEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .operatorId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.claimApprovalTaskByExpert(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "claim/by-platform", method = RequestMethod.POST)
    public Response<Void> claimApprovalTaskByPlatform(@RequestBody ClaimTaskRequestDTO requestDTO) {
        log.info("所属医院/机构管理员领取终审任务, 流程ID: {}", requestDTO.getCopywritingApprovalFlowId());
        CopywritingDataScope dataScope = currentDataScope();
        Long userId = dataScope.actorUserId();

        ClaimTaskEntity entity = ClaimTaskEntity.builder()
                .copywritingApprovalFlowId(requestDTO.getCopywritingApprovalFlowId())
                .operatorId(userId)
                .dataScope(dataScope)
                .build();

        copyWritingApprovalService.claimApprovalTaskByPlatform(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    private CopywritingDataScope currentDataScope() {
        return dataScopeFor(authService.queryValidatedUser());
    }

    private CopywritingDataScope dataScopeFor(AuthUserEntity authenticatedUser) {
        boolean allAccess = Boolean.TRUE.equals(authService.isAdmin()) || hasSuperAdminAuthority();
        return new CopywritingDataScope(
                authenticatedUser.getUserId(),
                allAccess,
                !allAccess && Boolean.TRUE.equals(authService.hasExpertRole()),
                !allAccess && Boolean.TRUE.equals(authService.hasOrganizationAdminRole()));
    }

    private boolean hasSuperAdminAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .anyMatch(role -> RoleCode.canonicalize(role)
                        .map(RoleCode.SUPER_ADMIN::equals)
                        .orElse(false));
    }
}
