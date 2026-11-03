package com.hzmwmt.domain.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.service.user.AdminConversationService;
import com.hzmwmt.domain.vo.AdminConversationDetailVO;
import com.hzmwmt.domain.vo.AdminConversationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Tag(
        name = "管理员对话管理",
        description = "用于管理员端的对话列表查询、详情查看、对话删除等操作"
)
@Slf4j
@Validated
@RestController
@PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/user/conversation")
public class AdminConversationController {

    @Resource
    private AdminConversationService adminConversationService;

    /**
     * 分页查询对话列表
     */
    @Operation(
            summary = "分页查询对话列表",
            description = "支持按用户名、问题内容模糊搜索，按状态、模型筛选，返回分页的对话基本信息（如ID、创建时间、状态等）"
    )
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认第1页", example = "1", required = false),
            @Parameter(name = "pageSize", description = "每页条数，默认10条", example = "10", required = false),
            @Parameter(name = "username", description = "搜索参数：用户名（模糊匹配）", example = "张三", required = false),
            @Parameter(name = "content", description = "搜索参数：问题内容（模糊匹配对话中的问题文本）", example = "如何使用API", required = false),
            @Parameter(name = "status", description = "搜索参数：对话状态（如'已完成'、'处理中'，具体值参考业务字典）", example = "completed", required = false),
            @Parameter(name = "model", description = "搜索参数：使用的AI模型ID（筛选指定模型的对话）", example = "1001", required = false)
    })
    @GetMapping("/page")
    public Result<IPage<AdminConversationVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long model) {
        IPage<AdminConversationVO> page = adminConversationService.getConversationList(pageNum, pageSize, username, content, status, model);
        return Result.success(page);
    }

    /**
     * 查看对话详情
     */
    @Operation(
            summary = "查看对话详情",
            description = "根据对话ID查询完整的对话详情，包含对话中的所有问题、回答、交互时间、使用的模型等信息"
    )
    @Parameter(
            name = "conversationId",
            description = "对话ID（主键，唯一标识一个对话）",
            required = true,
            example = "conv_123456"
    )
    @GetMapping("/detail/{conversationId}")
    public Result<AdminConversationDetailVO> getConversationDetail(@PathVariable String conversationId) {
        AdminConversationDetailVO detailVO = adminConversationService.getConversationDetail(conversationId);
        return Result.success(detailVO);
    }

    /**
     * 删除对话
     */
    @Operation(
            summary = "删除对话",
            description = "根据对话ID删除指定对话（业务上通常为逻辑删除，更新状态为'已删除'，而非物理删除数据）"
    )
    @Parameter(
            name = "conversationId",
            description = "对话ID（需要删除的对话主键）",
            required = true,
            example = "conv_123456"
    )
    @DeleteMapping("/delete/{conversationId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Boolean> deleteConversation(@PathVariable String conversationId) {
        boolean result = adminConversationService.deleteConversation(conversationId);
        return Result.success(result);
    }
}
