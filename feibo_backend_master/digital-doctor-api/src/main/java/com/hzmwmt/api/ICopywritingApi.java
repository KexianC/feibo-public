package com.hzmwmt.api;

import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiOffsetPageData;
import com.hzmwmt.api.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ICopywritingApi
 * @Date: 2025/9/10 14:07
 */
public interface ICopywritingApi {
    /**
     * 根据文案状态分页查询列表
     *
     * @param page              页码
     * @param size              每页数量
     * @param copywritingStatus 文案状态枚举
     * @return 分页的文案聚合信息
     */
    Response<ApiOffsetPageData<CopywritingResponseDTO>> queryCopywritingByCopywritingStatus(
            @Min(value = 1, message = "页码不能小于1")
            Integer page,
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100")
            Integer size,
            Integer copywritingStatus);

    /**
     * 根据审批状态分页查询列表
     *
     * @param page                 页码
     * @param size                 每页数量
     * @param approvalStatusEnumVO 审批状态枚举
     * @return 分页的审批文案聚合信息
     */
    Response<ApiOffsetPageData<ApprovalCopywritingResponseDTO>> queryCopywritingByApprovalStatus(
            @Min(value = 1, message = "页码不能小于1")
            Integer page,
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100")
            Integer size,
            @NotNull(message = "审批状态不能为空")
            @Min(value = 0, message = "审批状态错误")
            @Max(value = 5, message = "审批状态错误")
            Integer approvalStatusEnumVO);

    /**
     * 查询当前账号可见的审核记录。
     */
    @PreAuthorize("isAuthenticated()")
    Response<ApiOffsetPageData<CopywritingAuditRecordResponseDTO>> queryAuditRecords(
            @Min(value = 1, message = "页码不能小于1") Integer page,
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100") Integer size,
            Long copywritingId,
            String auditorRole,
            String auditResult);

    /** 查询指定文案的完整内容、当前流程和此前审核记录。 */
    @PreAuthorize("isAuthenticated()")
    Response<CopywritingAuditDetailResponseDTO> queryCopywritingAuditDetail(Long copywritingId);

    /** 查询指定文案的全部审核历史。 */
    @PreAuthorize("isAuthenticated()")
    Response<List<CopywritingAuditRecordResponseDTO>> queryCopywritingAuditHistory(Long copywritingId);

    /**
     * 查询所有有效的文案分类
     *
     * @return 文案分类列表
     */
    Response<List<CopywritingCategoryResponseDTO>> queryAllValidCopywritingCategory();

    /**
     * 查询所有有效的文案来源
     *
     * @return 文案来源列表
     */
    Response<List<CopywritingSourceResponseDTO>> queryAllValidCopywritingSource();

    /**
     * 提交新文案
     *
     * @param requestDTO 提交信息
     * @return 操作结果
     */
    Response<Void> submit(@Valid SubmitCopywritingRequestDTO requestDTO);

    /**
     * 作者自审/确认
     *
     * @param requestDTO 审批信息
     * @return 操作结果
     */
    @PreAuthorize("isAuthenticated()")
    Response<Void> approvalByDoctor(@Valid ApprovalRequestDTO requestDTO);


    /**
     * AI审批
     *
     * @param copywritingId
     * @return
     */
    @PreAuthorize("hasRole('ADMIN')")
    Response<Void> approvalByAI(@Valid long copywritingId);

    /**
     * 专家审批
     *
     * @param requestDTO 审批信息
     * @return 操作结果
     */
    @PreAuthorize("hasRole('EXPERT') or hasRole('ADMIN')")
    Response<Void> approvalByExpert(@Valid ApprovalRequestDTO requestDTO);

    /**
     * 所属医院/机构管理员终审
     *
     * @param requestDTO 审批信息
     * @return 操作结果
     */
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('ADMIN')")
    Response<Void> approvalByPlatform(@Valid ApprovalRequestDTO requestDTO);

    /**
     * 指派任务给助理
     *
     * @param requestDTO 指派信息
     * @return 操作结果
     */
    @PreAuthorize("denyAll()")
    Response<Void> delegateToAssistant(@Valid DelegateToAssistantRequestDTO requestDTO);

    /**
     * 专家领取任务
     *
     * @param requestDTO 领取信息
     * @return 操作结果
     */
    @PreAuthorize("hasRole('EXPERT') or hasRole('ADMIN')")
    Response<Void> claimApprovalTaskByExpert(@Valid ClaimTaskRequestDTO requestDTO);

    /**
     * 所属医院/机构管理员领取终审任务
     *
     * @param requestDTO 领取信息
     * @return 操作结果
     */
    @PreAuthorize("hasRole('ORG_ADMIN') or hasRole('ADMIN')")
    Response<Void> claimApprovalTaskByPlatform(@Valid ClaimTaskRequestDTO requestDTO);

    /**
     * 驳回审批
     *
     * @param requestDTO 驳回信息
     * @return 操作结果
     */
    @PreAuthorize("isAuthenticated()")
    Response<Void> reject(@Valid ApprovalRequestDTO requestDTO);

    /**
     * 创建文案分类
     *
     * @param name 分类名称
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
    Response<Void> createCopywritingCategory(
            @NotBlank(message = "分类名不能为空")
            @Size(max = 64, message = "分类名长度不能超过64个字符")
            String name);

    /**
     * 创建文案来源
     *
     * @param name 来源名称
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
    Response<Void> createCopywritingSource(
            @NotBlank(message = "来源名不能为空")
            @Size(max = 64, message = "来源名长度不能超过64个字符")
            String name);
}
