package com.hzmwmt.domain.controller.packages;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.packages.TAiRechargePack;
import com.hzmwmt.domain.service.packages.AdminAiRechargePackService;
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
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@PreAuthorize("hasAuthority('finance.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/ai-recharge-pack")
@Tag(name = "后台AI充值包管理", description = "用于管理员端的AI充值包增删改查操作")
public class AdminAiRechargePackController {

    @Resource
    private AdminAiRechargePackService adminAiRechargePackService;

    @Operation(summary = "分页查询AI充值包", description = "支持按充值包名称模糊搜索，自动过滤已删除数据")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10"),
            @Parameter(name = "name", description = "充值包名称（模糊匹配，可选）", example = "AI充值包700元")
    })
    @OperationLog(operationType = "查询", description = "分页查询AI充值包")
    @GetMapping("/page")
    public Result<IPage<TAiRechargePack>> page(
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) @Size(max = 64) String name,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        IPage<TAiRechargePack> resultPage = adminAiRechargePackService.pageWithCondition(pageNum, pageSize, name, status, minPrice, maxPrice);
        return Result.success(resultPage);
    }

    @Operation(summary = "根据ID查询AI充值包", description = "获取单条AI充值包详情")
    @ApiResponse(responseCode = "200", description = "查询成功",
            content = @Content(schema = @Schema(implementation = TAiRechargePack.class)))
    @OperationLog(operationType = "查询", description = "根据ID查询AI充值包")
    @GetMapping("/{id}")
    public Result<TAiRechargePack> getById(
            @Parameter(description = "AI充值包ID", example = "1") @PathVariable @Positive Integer id
    ) {
        TAiRechargePack packEntity = adminAiRechargePackService.getById(id);
        return Result.success(packEntity);
    }

    @Operation(summary = "新增AI充值包", description = "添加新的AI充值包信息")
    @ApiResponse(responseCode = "200", description = "新增成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "新增", description = "新增AI充值包", recordResult = false, critical = true)
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> save(
            @Parameter(description = "AI充值包实体信息（id无需传入，自动生成）", required = true)
            @Valid @RequestBody TAiRechargePack packEntity
    ) {
        packEntity.setId(null);
        packEntity.setAiRechargePackId(null);
        packEntity.setPackCode(null);
        packEntity.setCreateTime(null);
        packEntity.setUpdateTime(null);
        boolean result = adminAiRechargePackService.save(packEntity);
        return Result.success(result);
    }

    @Operation(summary = "更新AI充值包", description = "修改已有AI充值包信息（需传入ID）")
    @ApiResponse(responseCode = "200", description = "更新成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "修改", description = "更新AI充值包", recordResult = false, critical = true)
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> update(
            @Parameter(description = "AI充值包实体信息（必须包含id）", required = true)
            @Valid @RequestBody TAiRechargePack packEntity
    ) {
        if (packEntity.getId() == null || packEntity.getId() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        TAiRechargePack safeUpdate = new TAiRechargePack();
        safeUpdate.setId(packEntity.getId());
        safeUpdate.setName(packEntity.getName());
        safeUpdate.setPrice(packEntity.getPrice());
        safeUpdate.setAiFeatureTemplateId(packEntity.getAiFeatureTemplateId());
        safeUpdate.setValue(packEntity.getValue());
        safeUpdate.setDescription(packEntity.getDescription());
        safeUpdate.setIsActive(packEntity.getIsActive());
        boolean result = adminAiRechargePackService.updateById(safeUpdate);
        return Result.success(result);
    }

    @Operation(summary = "删除AI充值包", description = "删除AI充值包")
    @ApiResponse(responseCode = "200", description = "删除成功",
            content = @Content(schema = @Schema(type = "boolean", example = "true")))
    @OperationLog(operationType = "删除", description = "删除AI充值包", recordResult = false, critical = true)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> remove(
            @Parameter(description = "AI充值包ID", example = "1") @PathVariable @Positive Integer id
    ) {
        boolean result = adminAiRechargePackService.removeById(id);
        return Result.success(result);
    }
}
