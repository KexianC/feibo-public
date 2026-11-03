package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.service.function.AdminCopywritingApprovalService;
import com.hzmwmt.domain.service.function.dto.AdminCopywritingApprovalResponse;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/copywriting-approval")
@Tag(name = "文案审批管理", description = "用于管理员端的文案审核管理操作")
@Validated
public class AdminCopywritingApprovalController {

    @Autowired
    private AdminCopywritingApprovalService adminCopywritingApprovalService;

    @Operation(summary = "分页查询文案审批列表", description = "支持提交人姓名搜索和审核状态筛选")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，从1开始", required = true),
            @Parameter(name = "pageSize", description = "每页条数", required = true),
            @Parameter(name = "submitterName", description = "提交人姓名"),
            @Parameter(name = "status", description = "case 0 -> \"作者待确认\";\n" +
                    "            case 1 -> \"专家组待专业审核\";\n" +
                    "            case 2 -> \"AI自动审核中\";\n" +
                    "            case 3 -> \"所属医院/机构待终审\";\n" +
                    "            case 4 -> \"审批完成\";\n" +
                    "            case 5 -> \"审批驳回\";\n" +
                    "            default -> \"未知状态\";")
    })
    @OperationLog(operationType = "查询", description = "分页查询文案审批列表")
    @GetMapping("/page")
    public Result<IPage<AdminCopywritingApprovalResponse>> pageCopywritingApprovals(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String submitterName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status) {
        IPage<AdminCopywritingApprovalResponse> resultPage = adminCopywritingApprovalService.pageCopywritingApprovals(pageNum, pageSize, submitterName, status, title);
        return Result.success(resultPage);
    }

    @Operation(summary = "查询文案详情", description = "根据文案ID查询完整的文案内容")
    @OperationLog(operationType = "查询", description = "查询文案详情")
    @GetMapping("/detail/{copywritingId}")
    public Result<TCopywriting> getCopywritingDetail(
            @PathVariable("copywritingId")
            @NotNull(message = "文案ID不能为空") Long copywritingId) {
        TCopywriting copywriting = adminCopywritingApprovalService.getCopywritingDetail(copywritingId);
        return Result.success(copywriting);
    }
}
