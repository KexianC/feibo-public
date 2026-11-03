package com.hzmwmt.domain.controller.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.service.user.AdminDigitalWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 管理员数字作品管理控制器
 * （@Tag：标注控制器功能，在 Swagger 文档中作为模块标题）
 */
@Tag(
        name = "管理员数字作品管理",
        description = "提供数字作品的分页查询、单条删除、批量删除等管理员操作"
)
@Slf4j
@Validated
@PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
@RestController
@RequestMapping("/api/admin/user/digital-works")
public class AdminDigitalWorkController {

    @Resource
    private AdminDigitalWorkService adminDigitalWorkService;


    /**
     * 分页查询数字作品列表
     * （@Operation：描述接口功能；@Parameters：批量定义请求参数说明）
     */
    @Operation(
            summary = "查询永久保留的视频合成请求审计",
            description = "以只增不改的合成审计表为主表，关联作品与用户快照；支持多字段搜索，作品删除后审计仍可核查。",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查询成功",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Result.class)
                            )
                    ),
                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
            }
    )
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，最小为1", example = "1", required = false),
            @Parameter(name = "pageSize", description = "每页条数，限制为1至100", example = "10", required = false),
            @Parameter(name = "keyword", description = "搜索任务ID、作品ID、标题、昵称或手机号，最多100字符", example = "task-123", required = false)
    })
    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        IPage<Map<String, Object>> page =
                adminDigitalWorkService.pageAuditQuery(pageNum, pageSize, keyword);
        List<Map<String, Object>> resultRecords = page.getRecords();
        for (Map<String, Object> record : resultRecords) {
            Object archivedOnly = record.get("archived_only");
            if (archivedOnly instanceof Number number) {
                record.put("archived_only", number.intValue() != 0);
            } else if (!(archivedOnly instanceof Boolean)) {
                record.put("archived_only", false);
            }
        }

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("records", resultRecords);
        resultData.put("total", page.getTotal());
        resultData.put("current", page.getCurrent());
        resultData.put("size", page.getSize());
        resultData.put("pages", page.getPages());

        return Result.success(resultData);
    }

    @Operation(
            summary = "统计视频合成质量",
            description = "按审计记录的作品时间范围聚合总数、成功、失败、处理中和合成时长。"
    )
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            return Result.error(400, "结束日期不能早于开始日期");
        }
        Map<String, Object> source = adminDigitalWorkService.getAuditStatistics(startDate, endDate);
        Map<String, Object> result = new HashMap<>();
        result.put("total", source == null ? 0 : source.getOrDefault("total", 0));
        result.put("success", source == null ? 0 : source.getOrDefault("success_count", 0));
        result.put("failed", source == null ? 0 : source.getOrDefault("failed_count", 0));
        result.put("processing", source == null ? 0 : source.getOrDefault("processing_count", 0));
        result.put("totalDuration", source == null ? 0 : source.getOrDefault("total_duration", 0));
        return Result.success(result);
    }

    /**
     * 根据 ID 删除单个数字作品
     * （@Parameter：描述路径参数；@ApiResponses：细分不同响应场景）
     */
    @Operation(
            summary = "根据 ID 删除数字作品",
            description = "通过作品 ID 逻辑删除数字作品（标记为「已删除」状态，非物理删除）",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "删除成功",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Result.class)
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "作品 ID 不存在"),
                    @ApiResponse(responseCode = "500", description = "删除失败或服务器错误")
            }
    )
    @Parameter(
            name = "id",
            description = "数字作品 ID（主键，唯一标识作品）",
            required = true,
            example = "1001"
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Boolean> deleteById(@PathVariable Integer id) {
        boolean success = adminDigitalWorkService.deleteById(id);
        return success
                ? Result.success("删除成功")
                : Result.error(500, "删除失败（ID 不存在或无效）");
    }

    @Operation(
            summary = "批量删除数字作品",
            description = "通过 ID 列表批量逻辑删除数字作品，返回「是否全部删除成功」"
    )
// 正确描述数组类型的请求体
    @RequestBody(
            description = "要删除的数字作品 ID 列表",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = Integer[].class,  // 指定实现类型
                            description = "数字作品 ID 数组",
                            example = "[1001, 1002, 1003]"
                    ),
                    examples = {
                            @ExampleObject(
                                    name = "删除单个作品",
                                    summary = "删除单个数字作品",
                                    value = "[1001]"
                            ),
                            @ExampleObject(
                                    name = "删除多个作品",
                                    summary = "删除多个数字作品",
                                    value = "[1001, 1002, 1003]"
                            )
                    }
            )
    )
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Boolean> deleteBatch(@RequestBody List<Integer> ids) {
        boolean success = adminDigitalWorkService.deleteBatch(ids);
        return success
                ? Result.success("批量删除成功")
                : Result.error(500, "批量删除失败（部分 ID 无效）");
    }

    /**
     * 统计视频合成时长排行
     *
     * @param topNum
     * @return
     */
    @Operation(summary = "统计视频合成时长排行耪")
    @GetMapping("/video-ranking")
    @Parameter(name = "topNum", description = "排行人数（默认前10名）", example = "10")
    public Result<List<Map<String, Integer>>> getTopUser(@RequestParam(required = false) Integer topNum,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) return Result.error(400, "结束日期不能早于开始日期");
        return Result.success(adminDigitalWorkService.getTopUser(topNum, startDate, endDate));
    }
}
