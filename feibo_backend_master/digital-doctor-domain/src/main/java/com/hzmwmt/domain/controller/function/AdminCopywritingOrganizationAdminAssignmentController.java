package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.service.function.AdminCopywritingOrganizationAdminAssignmentService;
import com.hzmwmt.domain.vo.AdminCopywritingOrganizationAdminAssignmentVO;
import com.hzmwmt.domain.vo.AdminReviewAccountOptionVO;
import com.hzmwmt.domain.vo.AdminReviewOrganizationAdminOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/copywriting-organization-admin-assignment")
@Tag(name = "Copywriting organization final-review assignment")
public class AdminCopywritingOrganizationAdminAssignmentController {

    @Resource
    private AdminCopywritingOrganizationAdminAssignmentService assignmentService;

    @GetMapping("/accounts")
    @Operation(summary = "Query accounts available for organization final-review assignment")
    public Result<IPage<AdminReviewAccountOptionVO>> queryAccounts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(assignmentService.queryAccounts(page, size, keyword));
    }

    @PostMapping("/query")
    @Operation(summary = "Query organization final-review assignments for accounts")
    public Result<List<AdminCopywritingOrganizationAdminAssignmentVO>> queryAssignments(
            @RequestBody List<Long> accountUserIds) {
        return Result.success(assignmentService.queryAssignments(accountUserIds));
    }

    @GetMapping("/admins")
    @Operation(summary = "Query active organization final-review administrators")
    public Result<List<AdminReviewOrganizationAdminOptionVO>> queryAdminOptions(
            @RequestParam(required = false) String keyword) {
        return Result.success(assignmentService.queryAdminOptions(keyword));
    }

    @PutMapping("/{accountUserId}")
    @Operation(summary = "Replace all organization final-review administrators assigned to an account")
    public Result<List<AdminCopywritingOrganizationAdminAssignmentVO>> replaceAssignments(
            @PathVariable Long accountUserId,
            @RequestBody List<Long> adminUserIds) {
        return Result.success(assignmentService.replaceAssignments(accountUserId, adminUserIds));
    }
}
