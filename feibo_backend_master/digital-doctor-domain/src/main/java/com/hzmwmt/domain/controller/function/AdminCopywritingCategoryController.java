package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import com.hzmwmt.domain.service.function.AdminCopywritingCategoryService;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/copywriting-category")
@Tag(name = "文案分类管理", description = "用于管理员端的文案分类增删改查操作")
public class AdminCopywritingCategoryController {

    @Resource
    private AdminCopywritingCategoryService adminCopywritingCategoryService;

    @Operation(summary = "分页查询文案分类", description = "支持分页获取文案分类列表")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10")
    })
    @OperationLog(operationType = "查询", description = "分页查询文案分类")
    @GetMapping("/page")
    public Result<IPage<TCopywritingCategory>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title
    ) {
        IPage<TCopywritingCategory> resultPage = adminCopywritingCategoryService.page(new Page<>(Math.max(1, pageNum), Math.min(100, Math.max(1, pageSize))), new LambdaQueryWrapper<TCopywritingCategory>().like(title != null && !title.isBlank(), TCopywritingCategory::getName, title).orderByDesc(TCopywritingCategory::getId));
        return Result.success(resultPage);
    }

    @Operation(summary = "根据ID查询分类详情", description = "通过主键ID获取单条分类信息")
    @ApiResponse(responseCode = "200", description = "查询成功",
            content = @Content(schema = @Schema(implementation = TCopywritingCategory.class)))
    @OperationLog(operationType = "查询", description = "根据ID查询分类详情")
    @GetMapping("/{id}")
    public Result<TCopywritingCategory> getById(
            @Parameter(description = "分类ID", example = "1") @PathVariable Long id
    ) {
        TCopywritingCategory category = adminCopywritingCategoryService.getById(id);
        return Result.success(category);
    }

    @Operation(summary = "新增文案分类", description = "添加新的文案分类（ID自动生成）")
    @OperationLog(operationType = "新增", description = "新增文案分类")
    @PostMapping
    public Result<TCopywritingCategory> save(
            @Parameter(description = "文案分类信息（无需传ID）", required = true)
            @RequestBody TCopywritingCategory category
    ) {
        TCopywritingCategory created = fields(category);
        created.setCopywritingCategoryId(IdWorker.getId());
        if (!adminCopywritingCategoryService.save(created)) throw new ResponseStatusException(HttpStatus.CONFLICT, "分类未创建");
        return Result.success(created);
    }

    @Operation(summary = "修改文案分类", description = "根据ID更新分类信息")
    @OperationLog(operationType = "修改", description = "修改文案分类")
    @PutMapping
    public Result<Boolean> update(
            @Parameter(description = "文案分类信息（需包含ID）", required = true)
            @RequestBody TCopywritingCategory category
    ) {
        if (category.getId() == null || category.getId() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类ID无效");
        TCopywritingCategory update = fields(category); update.setId(category.getId());
        boolean result = adminCopywritingCategoryService.updateById(update);
        return Result.success(result);
    }

    @Operation(summary = "删除文案分类", description = "根据ID删除指定分类")
    @OperationLog(operationType = "删除", description = "删除文案分类")
    @DeleteMapping("/{id}")
    public Result<Boolean> remove(
            @Parameter(description = "分类ID", example = "1") @PathVariable Long id
    ) {
        boolean result = adminCopywritingCategoryService.removeById(id);
        return Result.success(result);
    }
    private TCopywritingCategory fields(TCopywritingCategory request) {
        if (request.getName() == null || request.getName().isBlank() || request.getName().length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类名称须为1到100字");
        TCopywritingCategory result = new TCopywritingCategory(); result.setName(request.getName().trim());
        // The client category catalogue uses status=false for enabled.
        result.setStatus(request.getStatus() == null ? false : request.getStatus()); return result;
    }
}
