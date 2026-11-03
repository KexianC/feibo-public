package com.hzmwmt.domain.controller.user;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.dto.AdminAiModelApiKeyDTO;
import com.hzmwmt.domain.dto.AdminAiModelCreateDTO;
import com.hzmwmt.domain.dto.AdminAiModelOrderDTO;
import com.hzmwmt.domain.dto.AdminAiModelUpdateDTO;
import com.hzmwmt.domain.dto.AdminAiModelVisibilityDTO;
import com.hzmwmt.domain.service.user.AdminModelConfigService;
import com.hzmwmt.domain.vo.AdminAiModelVO;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/v1/admin/ai-models")
@Tag(name = "智笔医言模型管理", description = "管理通用模型的展示信息与调用凭据")
public class AdminAiModelController {

    @Resource
    private AdminModelConfigService adminModelConfigService;

    @GetMapping
    @Operation(summary = "查询智笔医言通用模型")
    public Result<List<AdminAiModelVO>> list() {
        return Result.success(adminModelConfigService.listManagedModels());
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "新增智笔医言模型")
    @OperationLog(
            operationType = "新增",
            description = "新增智笔医言模型",
            recordParams = false,
            recordResult = false,
            critical = true)
    public Result<AdminAiModelVO> create(
            @Valid @RequestBody AdminAiModelCreateDTO request) {
        return Result.success(adminModelConfigService.createManagedModel(request));
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "更新模型名称、供应商模型 ID、简介或排序")
    @OperationLog(
            operationType = "修改",
            description = "更新智笔医言模型信息",
            recordParams = false,
            recordResult = false,
            critical = true)
    public Result<AdminAiModelVO> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AdminAiModelUpdateDTO request) {
        return Result.success(adminModelConfigService.updateManagedModel(id, request));
    }

    @PutMapping("/providers/{providerId}/order")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "更新智笔医言供应商菜单排序")
    @OperationLog(
            operationType = "修改",
            description = "更新智笔医言供应商菜单排序",
            recordParams = false,
            recordResult = false,
            critical = true)
    public Result<List<AdminAiModelVO>> updateProviderOrder(
            @PathVariable @Positive Long providerId,
            @Valid @RequestBody AdminAiModelOrderDTO request) {
        return Result.success(adminModelConfigService.updateProviderOrder(
                providerId,
                request.getOrder()));
    }

    @PutMapping("/{id}/visibility")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "设置模型是否在智笔医言前台显示")
    @OperationLog(
            operationType = "修改",
            description = "设置智笔医言模型显示状态",
            recordParams = false,
            recordResult = false,
            critical = true)
    public Result<AdminAiModelVO> updateVisibility(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AdminAiModelVisibilityDTO request) {
        return Result.success(
                adminModelConfigService.updateVisibility(id, request.getVisible()));
    }

    @PutMapping("/{id}/api-key")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "更新模型 API Key", description = "空白 API Key 表示保留旧值")
    @OperationLog(
            operationType = "修改",
            description = "更新智笔医言模型 API Key",
            recordParams = false,
            recordResult = false,
            critical = true)
    public Result<AdminAiModelVO> updateApiKey(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AdminAiModelApiKeyDTO request) {
        return Result.success(adminModelConfigService.updateApiKey(id, request.getApiKey()));
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "从管理列表移除已隐藏的智笔医言模型并清除 Key 覆盖")
    @OperationLog(
            operationType = "删除",
            description = "删除智笔医言模型",
            recordParams = false,
            recordResult = false,
            targetIdParameterIndex = 0,
            critical = true)
    public Result<Void> delete(@PathVariable @Positive Long id) {
        adminModelConfigService.deleteManagedModel(id);
        return Result.success();
    }
}
