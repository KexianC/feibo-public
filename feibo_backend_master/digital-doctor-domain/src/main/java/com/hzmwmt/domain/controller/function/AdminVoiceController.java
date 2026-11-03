package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TDigitalVoice;
import com.hzmwmt.domain.service.function.AdminVoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.hzmwmt.domain.dto.AdminMaterialWriteRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音管理接口（前端页面交互入口）
 */
@Tag(
        name = "管理员语音管理",
        description = "用于管理员端的数字语音作品管理，包含分页查询、新增、编辑、删除、单条查询等操作"
)
@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/voice")
public class AdminVoiceController {

    @Resource
    private AdminVoiceService adminVoiceService;

    /**
     * 分页查询语音列表
     * 支持按名称、性别筛选
     */
    @Operation(
            summary = "分页查询语音列表",
            description = "获取数字语音作品的分页数据，支持按语音名称模糊搜索、性别筛选（如男/女/中性）",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查询成功，返回分页语音列表",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Result.class)
                            )
                    ),
                    @ApiResponse(responseCode = "500", description = "服务器内部错误，查询失败")
            }
    )
    @Parameters({
            @Parameter(
                    name = "pageNum",
                    description = "页码，默认第1页",
                    example = "1",
                    required = false
            ),
            @Parameter(
                    name = "pageSize",
                    description = "每页条数，默认10条",
                    example = "10",
                    required = false
            ),
            @Parameter(
                    name = "name",
                    description = "筛选参数：语音名称（模糊匹配）",
                    example = "甜美女声",
                    required = false
            ),
            @Parameter(
                    name = "gender",
                    description = "筛选参数：语音性别（如'male'-男、'female'-女、'neutral'-中性，具体值参考业务字典）",
                    example = "female",
                    required = false
            )
    })
    @GetMapping("/page")
    public Result<IPage<TDigitalVoice>> pageQuery(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender) {

        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("gender", gender);
        IPage<TDigitalVoice> page = adminVoiceService.pageQuery(pageNum, pageSize, params);
        return Result.success(page);
    }

    /**
     * 新增数字语音作品
     */
    @Operation(
            summary = "新增数字语音作品",
            description = "提交名称、供应商语音 ID、风格、性别和显示状态，创建数字语音作品",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "新增成功",
                            content = @Content(schema = @Schema(implementation = Result.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "新增失败（如参数无效、重复创建等）"
                    )
            }
    )
    @Parameter(
            name = "voice",
            description = "新增语音的参数信息（包含名称、供应商语音 ID、风格、性别和显示状态）",
            required = true,
            content = @Content(schema = @Schema(implementation = TDigitalVoice.class))
    )
    @PostMapping
    public Result<Boolean> saveVoice(@Valid @RequestBody AdminMaterialWriteRequest.VoiceCreate request) {
        TDigitalVoice voice = new TDigitalVoice();
        voice.setName(request.name().trim());
        voice.setDigitalVoiceId(request.digitalVoiceId());
        voice.setStyleName(request.styleName());
        voice.setGender(request.gender());
        voice.setIsDisplay(request.isDisplay());
        voice.setIsDelete(false);
        boolean success = adminVoiceService.saveVoice(voice);
        return success ? Result.success(true) : Result.error("新增语音失败");
    }

    @Operation(summary = "编辑数字语音作品", description = "更新名称、供应商语音 ID、风格、性别和显示状态")
    @PutMapping
    public Result<Boolean> updateVoice(@Valid @RequestBody AdminMaterialWriteRequest.VoiceUpdate request) {
        TDigitalVoice existing = adminVoiceService.getVoiceById(request.id().longValue());
        if (existing == null || Boolean.TRUE.equals(existing.getIsDelete())) {
            return Result.error(404, "语音不存在");
        }
        TDigitalVoice voice = new TDigitalVoice();
        voice.setId(request.id());
        voice.setName(request.name().trim());
        voice.setDigitalVoiceId(request.digitalVoiceId());
        voice.setStyleName(request.styleName().trim());
        voice.setGender(request.gender());
        voice.setIsDisplay(request.isDisplay());
        voice.setUpdateTime(java.time.LocalDateTime.now());
        return adminVoiceService.updateVoice(voice) ? Result.success(true) : Result.error("编辑语音失败");
    }

    /**
     * 根据ID删除语音作品
     */
    @Operation(
            summary = "根据ID删除语音作品",
            description = "通过语音ID删除指定的数字语音作品（业务上通常为逻辑删除，更新状态为'已删除'）",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "删除成功",
                            content = @Content(schema = @Schema(implementation = Result.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "语音不存在（ID无效或已被删除）"
                    ),
                    @ApiResponse(responseCode = "500", description = "删除失败")
            }
    )
    @Parameter(
            name = "id",
            description = "语音作品ID（主键，唯一标识一个语音）",
            required = true,
            example = "2001"
    )
    @DeleteMapping("/{id}")
    public Result<Boolean> removeVoice(@PathVariable Long id) {
        boolean success = adminVoiceService.removeVoice(id);
        return success ? Result.success(true) : Result.error("删除失败：语音不存在");
    }

    /**
     * 根据ID查询语音详情
     */
    @Operation(
            summary = "根据ID查询语音详情",
            description = "通过语音ID获取完整的语音信息（如名称、性别、创建时间、音频地址等）",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查询成功，返回语音详情",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Result.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "语音不存在（ID无效或已被删除）"
                    )
            }
    )
    @Parameter(
            name = "id",
            description = "语音作品ID（主键）",
            required = true,
            example = "2001"
    )
    @GetMapping("/{id}")
    public Result<TDigitalVoice> getVoiceById(@PathVariable Long id) {
        TDigitalVoice voice = adminVoiceService.getVoiceById(id);
        return voice != null ? Result.success(voice) : Result.error(404, "语音不存在");
    }
}
