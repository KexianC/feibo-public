package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.service.function.AdminCopywritingService;
import com.hzmwmt.domain.vo.AdminCopywritingVO;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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
@RequestMapping("/api/admin/function/copywriting")
@Tag(name = "文案管理", description = "用于管理员端的文案增删改查操作")
public class AdminCopywritingController {

    @Resource
    private AdminCopywritingService adminCopywritingService;

    @Operation(summary = "分页查询文案", description = "支持标题、分类ID筛选")
    @Parameters({
            @Parameter(name = "pageNum", example = "1", description = "页码"),
            @Parameter(name = "pageSize", example = "10", description = "每页条数"),
            @Parameter(name = "title", example = "心绞痛", description = "标题模糊搜索"),
            @Parameter(name = "categoryId", example = "2", description = "分类ID")
    })
    @GetMapping("/page")
    public Result<IPage<AdminCopywritingVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId
    ) {
        IPage<AdminCopywritingVO> resultPage = adminCopywritingService.pageCopywritings(pageNum, pageSize, title, categoryId);
        return Result.success(resultPage);
    }

    @Operation(summary = "分页查询待专家组专业审核的文案", description = "查询current_status=1的文案，支持标题、分类ID筛选")
    @Parameters({
            @Parameter(name = "pageNum", example = "1", description = "页码"),
            @Parameter(name = "pageSize", example = "10", description = "每页条数"),
            @Parameter(name = "title", example = "心绞痛", description = "标题模糊搜索"),
            @Parameter(name = "categoryId", example = "2", description = "分类ID")
    })
    @OperationLog(operationType = "查询", description = "分页查询待专家组专业审核的文案")
    @GetMapping("/page/expert-approval")
    public Result<IPage<AdminCopywritingVO>> pageExpertApproval(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId
    ) {
        IPage<AdminCopywritingVO> resultPage = adminCopywritingService.pageExpertApprovalCopywritings(pageNum, pageSize, title, categoryId);
        return Result.success(resultPage);
    }

    @Operation(summary = "分页查询所属医院/机构待终审文案", description = "查询current_status=3的文案，支持标题、分类ID筛选")
    @Parameters({
            @Parameter(name = "pageNum", example = "1", description = "页码"),
            @Parameter(name = "pageSize", example = "10", description = "每页条数"),
            @Parameter(name = "title", example = "心绞痛", description = "标题模糊搜索"),
            @Parameter(name = "categoryId", example = "2", description = "分类ID")
    })
    @OperationLog(operationType = "查询", description = "分页查询所属医院/机构待终审文案")
    @GetMapping("/page/platform-approval")
    public Result<IPage<AdminCopywritingVO>> pagePlatformApproval(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId
    ) {
        IPage<AdminCopywritingVO> resultPage = adminCopywritingService.pagePlatformApprovalCopywritings(pageNum, pageSize, title, categoryId);
        return Result.success(resultPage);
    }

    @Operation(summary = "分页查询机构终审通过的文案", description = "查询current_status=4的文案，支持标题、分类ID筛选")
    @Parameters({
            @Parameter(name = "pageNum", example = "1", description = "页码"),
            @Parameter(name = "pageSize", example = "10", description = "每页条数"),
            @Parameter(name = "title", example = "心绞痛", description = "标题模糊搜索"),
            @Parameter(name = "categoryId", example = "2", description = "分类ID")
    })
    @OperationLog(operationType = "查询", description = "分页查询机构终审通过的文案")
    @GetMapping("/page/platform-approved")
    public Result<IPage<AdminCopywritingVO>> pagePlatformApproved(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId
    ) {
        IPage<AdminCopywritingVO> resultPage = adminCopywritingService.pagePlatformApprovedCopywritings(pageNum, pageSize, title, categoryId);
        return Result.success(resultPage);
    }

    @Operation(summary = "新增文案", description = "传入文案信息（含标题、分类ID等）")
    @OperationLog(operationType = "新增", description = "新增文案")
    @PostMapping
    public Result<Boolean> add(@RequestBody TCopywriting copywriting) {
        boolean result = adminCopywritingService.createCopywritingSafely(copywriting);
        return Result.success(result);
    }

    @Operation(summary = "根据ID查询文案")
    @OperationLog(operationType = "查询", description = "根据ID查询文案")
    @GetMapping("/{id}")
    public Result<AdminCopywritingVO> getById(@PathVariable Long id) {
        AdminCopywritingVO copywriting = adminCopywritingService.getById(id);
        return Result.success(copywriting);
    }

    @Operation(summary = "更新文案", description = "传入含ID的文案信息")
    @OperationLog(operationType = "修改", description = "更新文案")
    @PutMapping
    public Result<Boolean> update(@RequestBody TCopywriting copywriting) {
        boolean result = adminCopywritingService.updateCopywritingSafely(copywriting);
        return Result.success(result);
    }

    @Operation(summary = "删除文案（逻辑删除）")
    @OperationLog(operationType = "删除", description = "删除文案")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        boolean result = adminCopywritingService.removeCopywritingSafely(id);
        return Result.success(result);
    }

    @Operation(summary = "查询分类名称")
    @OperationLog(operationType = "查询", description = "查询文案分类名称")
    @GetMapping("/category/name/{categoryId}")
    public Result<String> getCategoryName(@PathVariable Long categoryId) {
        String categoryName = adminCopywritingService.getCategoryName(categoryId);
        return Result.success(categoryName);
    }
}
