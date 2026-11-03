package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TCaseVideo;
import com.hzmwmt.domain.service.function.AdminCaseVideoService;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.hzmwmt.domain.dto.AdminMaterialWriteRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/function/case-video")
@Tag(name = "AdminCaseVideoController", description = "后台案例视频管理接口（含封面图）")
public class AdminCaseVideoController {

    @Resource
    private AdminCaseVideoService adminCaseVideoService;

    /**
     * 分页查询案例视频列表
     */
    @Operation(summary = "分页查询案例视频", description = "支持标题、场景、行业多条件筛选")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认1", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数，默认10", example = "10"),
            @Parameter(name = "title", description = "标题模糊搜索（可选）", example = "朱薛锋"),
            @Parameter(name = "scene", description = "场景分类（可选）", example = "临床应用"),
            @Parameter(name = "industry", description = "行业分类（可选）", example = "医疗健康")
    })
    @GetMapping("/page")
    public IPage<TCaseVideo> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String industry
    ) {
        return adminCaseVideoService.pageCaseVideos(pageNum, pageSize, title, scene, industry);
    }


    /**
     * 新增案例视频（合并上传文件和保存信息）
     * 仅需提供文件和基本信息，一次性完成上传和保存
     */
    @Operation(summary = "新增案例视频（合并上传文件和保存信息）", description = "一次性完成文件上传和信息保存")
    @PostMapping("/add-with-files")
    @OperationLog(description = "新增案例视频（合并上传）")
    public Result<Boolean> addWithFiles(
            @Parameter(description = "视频标题", required = true, example = "朱薛锋 丽水市人民医院") @RequestParam("title") String title,
            @Parameter(description = "场景分类（可选）", example = "临床应用") @RequestParam(value = "scene", required = false) String scene,
            @Parameter(description = "行业分类（可选）", example = "医疗健康") @RequestParam(value = "industry", required = false) String industry,
            @Parameter(description = "视频文件（支持 mp4/webm 等）", required = true) @RequestParam("videoFile") MultipartFile file,
            @Parameter(description = "封面图文件（支持 png/jpg 等）", required = true) @RequestParam("coverImage") MultipartFile cover,
            @RequestParam(defaultValue = "0") Integer sort,
            @RequestParam(defaultValue = "1") Integer isShow
    ) {
        if (file == null || file.isEmpty()) {
            return Result.error(Result.PARAM_ERROR, "请选择视频文件");
        }
        if (cover == null || cover.isEmpty()) {
            return Result.error(Result.PARAM_ERROR, "请选择封面图片");
        }
        validateFields(title, sort, isShow);
        boolean result = adminCaseVideoService.addCaseVideoWithFiles(title.trim(), scene, industry, file, cover, sort, isShow);
        return result ? Result.success(true) : Result.error(Result.OPERATION_FAILED, "保存失败");
    }


    /**
     * 根据 ID 查询案例视频
     */
    @Operation(summary = "根据 ID 查询案例视频", description = "获取单条案例视频的完整信息（含封面图、视频链接）")
    @GetMapping("/{id}")
    public TCaseVideo getById(
            @Parameter(description = "案例视频 ID", example = "84") @PathVariable Long id
    ) {
        return adminCaseVideoService.getById(id);
    }

    /**
     * 更新案例视频
     */
    @Operation(summary = "更新案例视频", description = "需传入 ID，支持修改标题、场景、封面图链接等信息")
    @PutMapping
    public boolean update(
            @Parameter(description = "案例视频信息（必须包含 ID）", required = true)
            @Valid @RequestBody AdminMaterialWriteRequest.CaseVideoUpdate request
    ) {
        TCaseVideo caseVideo = editRecord(request.id(), request.title(), request.scene(), request.industry(), request.sort(), request.isShow());
        return adminCaseVideoService.updateById(caseVideo);
    }

    /**
     * 删除案例视频（逻辑删除）
     */
    @Operation(summary = "删除案例视频", description = "逻辑删除（标记为「已删除」，保留数据可恢复）")
    @DeleteMapping("/{id}")
    public boolean delete(
            @Parameter(description = "案例视频 ID", example = "84") @PathVariable Long id
    ) {
        return adminCaseVideoService.removeById(id);
    }
    @PostMapping("/update-with-files")
    @OperationLog(description = "编辑案例视频并替换文件")
    public Result<Boolean> updateWithFiles(@RequestParam Long id, @RequestParam String title,
            @RequestParam(required = false) String scene, @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "0") Integer sort, @RequestParam(defaultValue = "1") Integer isShow,
            @RequestParam(required = false) MultipartFile videoFile,
            @RequestParam(required = false) MultipartFile coverImage) {
        validateFields(title, sort, isShow);
        if (id == null || id <= 0 || adminCaseVideoService.getById(id) == null) return Result.error("案例视频不存在");
        TCaseVideo record = editRecord(id, title.trim(), scene, industry, sort, isShow);
        if (videoFile != null && !videoFile.isEmpty()) record.setVideoUrl(adminCaseVideoService.uploadVideo(videoFile));
        if (coverImage != null && !coverImage.isEmpty()) record.setCoverUrl(adminCaseVideoService.uploadCoverImage(coverImage));
        boolean saved = adminCaseVideoService.updateById(record);
        return saved ? Result.success(true) : Result.error("案例视频保存失败");
    }

    private static void validateFields(String title, Integer sort, Integer isShow) {
        if (title == null || title.isBlank() || title.length() > 200) throw new IllegalArgumentException("请输入有效标题");
        if (sort == null || sort < 0 || isShow == null || (isShow != 0 && isShow != 1)) throw new IllegalArgumentException("排序或显示状态无效");
    }

    private static TCaseVideo editRecord(Long id, String title, String scene, String industry, Integer sort, Integer isShow) {
        TCaseVideo record = new TCaseVideo();
        record.setId(id); record.setTitle(title); record.setScene(scene); record.setIndustry(industry);
        record.setSort(sort); record.setIsShow(isShow);
        return record;
    }

}
