package com.hzmwmt.domain.controller.packages;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.AdminMembershipPlanStatusDTO;
import com.hzmwmt.domain.entity.packages.TAiMembershipPlan;
import com.hzmwmt.domain.service.packages.AdminAiMembershipPlanService;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * 后台AI会员套餐管理控制器
 */
@Slf4j
@Validated
@RestController
@PreAuthorize("hasAuthority('finance.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/ai-membership-plan")
@Tag(name = "后台AI会员套餐管理", description = "用于管理员端的AI会员套餐增删改查操作")
public class AdminAiMembershipPlanController {

    @Resource
    private AdminAiMembershipPlanService adminAiMembershipPlanService;

    /**
     * 分页查询AI会员套餐列表
     */
    @Operation(summary = "分页查询AI会员套餐列表", description = "支持按套餐名称模糊搜索，自动过滤已禁用数据")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10"),
            @Parameter(name = "planName", description = "套餐名称（模糊匹配，可选）", example = "年度AI会员")
    })
    @OperationLog(operationType = "查询", description = "分页查询AI会员套餐列表")
    @GetMapping("/page")
    public Result<IPage<TAiMembershipPlan>> page(
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) @Size(max = 64) String planName
    ) {
        IPage<TAiMembershipPlan> resultPage = adminAiMembershipPlanService.pageWithCondition(pageNum, pageSize, planName);
        return Result.success(resultPage);
    }

    /**
     * 根据ID查询AI会员套餐详情
     */
    @Operation(summary = "根据ID查询AI会员套餐", description = "获取单条AI会员套餐的完整信息")
    @ApiResponse(responseCode = "200", description = "查询成功",
            content = @Content(schema = @Schema(implementation = TAiMembershipPlan.class)))
    @OperationLog(operationType = "查询", description = "根据ID查询AI会员套餐")
    @GetMapping("/{id}")
    public Result<TAiMembershipPlan> getById(
            @Parameter(description = "套餐ID", required = true, example = "1")
            @PathVariable @Positive Integer id
    ) {
        TAiMembershipPlan plan = adminAiMembershipPlanService.getById(id);
        return Result.success(plan);
    }

    /**
     * 新增AI会员套餐
     */
    @Operation(summary = "新增AI会员套餐", description = "添加新的AI会员套餐信息")
    @ApiResponse(responseCode = "200", description = "新增成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "新增", description = "新增AI会员套餐", recordResult = false, critical = true)
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> save(
            @Parameter(description = "AI会员套餐实体信息（id无需传入，自动生成）", required = true)
            @Valid @RequestBody TAiMembershipPlan plan
    ) {
        long businessId = IdWorker.getId();
        plan.setId(null);
        plan.setAiMembershipPlanId(businessId);
        plan.setPlanCode("PLAN_" + Long.toUnsignedString(businessId, 36).toUpperCase(Locale.ROOT));
        plan.setCreateTime(null);
        plan.setUpdateTime(null);
        boolean result = adminAiMembershipPlanService.save(plan);
        return Result.success(result);
    }

    /**
     * 更新AI会员套餐信息
     */
    @Operation(summary = "更新AI会员套餐", description = "修改已有AI会员套餐信息，需传入ID及更新字段")
    @ApiResponse(responseCode = "200", description = "更新成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "修改", description = "更新AI会员套餐", recordResult = false, critical = true)
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> update(
            @Parameter(description = "AI会员套餐实体信息（必须包含id）", required = true)
            @Valid @RequestBody TAiMembershipPlan plan
    ) {
        if (plan.getId() == null || plan.getId() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        TAiMembershipPlan safeUpdate = new TAiMembershipPlan();
        safeUpdate.setId(plan.getId());
        safeUpdate.setName(plan.getName());
        safeUpdate.setPrice(plan.getPrice());
        safeUpdate.setDurationDays(plan.getDurationDays());
        safeUpdate.setIsActive(plan.getIsActive());
        boolean result = adminAiMembershipPlanService.updateById(safeUpdate);
        return Result.success(result);
    }

    /**
     * 启用/禁用AI会员套餐
     */
    @Operation(summary = "启用/禁用AI会员套餐", description = "设置套餐的启用状态（true=启用，false=禁用）")
    @ApiResponse(responseCode = "200", description = "操作成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "修改", description = "启用/禁用AI会员套餐", recordResult = false, critical = true)
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> updateStatus(
            @Parameter(description = "套餐ID", required = true, example = "1")
            @PathVariable @Positive Integer id,
            @Parameter(description = "显式启用状态", required = true)
            @Valid @RequestBody AdminMembershipPlanStatusDTO request
    ) {
        TAiMembershipPlan plan = new TAiMembershipPlan();
        plan.setId(id);
        plan.setIsActive(request.getIsActive());
        boolean result = adminAiMembershipPlanService.updateById(plan);
        return Result.success(result);
    }
}
