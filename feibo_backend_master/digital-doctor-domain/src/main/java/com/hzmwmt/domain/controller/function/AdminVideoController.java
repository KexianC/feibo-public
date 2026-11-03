package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.entity.function.TVideo;
import com.hzmwmt.domain.service.function.AdminVideoService;
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
@RequestMapping("/api/admin/function/video")
@Tag(name = "AdminVideoController", description = "后台视频管理接口（含时长）")
public class AdminVideoController {

    @Resource
    private AdminVideoService adminVideoService;

    /**
     * 分页查询 + 标题模糊搜索
     */
    @Operation(summary = "分页查询视频列表", description = "支持标题模糊搜索，包含时长信息")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认1", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数，默认10", example = "10"),
            @Parameter(name = "title", description = "标题模糊搜索（可选）", example = "测试")
    })
    @GetMapping("/page")
    public IPage<TVideo> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title
    ) {
        return adminVideoService.pageWithLike(pageNum, pageSize, title);
    }

    /*
     * 上传封面到OSS
     * 已注释：现通过新增视频接口直接上传封面
     */
    /*
    @Operation(summary = "上传视频封面", description = "支持 jpg/png 等格式，返回 OSS 链接")
    @ApiResponse(responseCode = "200", description = "上传成功",
            content = @Content(schema = @Schema(example = "https://xxx.oss-cn-beijing.aliyuncs.com/video/cover/xxx.jpg")))
    @PostMapping("/upload/cover")
    public String uploadCover(
            @Parameter(description = "封面文件", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        return adminVideoService.uploadCoverToOss(file);
    }

//    /**
//     * 上传视频到OSS（返回URL和时长）
//     */
//    @Operation(summary = "上传视频文件", description = "支持 mp4/webm 等格式，自动解析时长并返回")
//    @ApiResponse(responseCode = "200", description = "上传成功",
//            content = @Content(schema = @Schema(implementation = VideoUploadResult.class)))
//    @PostMapping("/upload/video")
//    public VideoUploadResult uploadVideo(
//            @Parameter(description = "视频文件", required = true)
//            @RequestParam("file") MultipartFile file
//    ) {
//        return adminVideoService.uploadVideoToOss(file); // 修改：返回包含时长的结果
//    }


    /**
     * 新增视频（含时长）
     * 直接上传视频文件和封面文件，自动解析视频时长
     */
    @Operation(summary = "新增视频", description = "直接上传视频文件和封面文件，自动处理上传到OSS并保存信息")
    @PostMapping
    public boolean save(
            @Parameter(description = "视频标题", required = true)
            @RequestParam String title,
            @Parameter(description = "排序值", required = false)
            @RequestParam(required = false) Integer sort,
            @Parameter(description = "视频文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "封面文件", required = true)
            @RequestParam("coverFile") MultipartFile coverFile
    ) {
        TVideo video = new TVideo();
        video.setTitle(title);
        if (sort != null) {
            video.setSort(sort);
        }
        return adminVideoService.saveWithFileUpload(video, file, coverFile);
    }

    /**
     * 更新视频（支持更新时长）
     */
    @Operation(summary = "更新视频", description = "可更新时长等信息，需传入ID")
    @PutMapping
    public boolean update(
            @Parameter(description = "视频信息（含ID）", required = true)
            @Valid @RequestBody AdminMaterialWriteRequest.VideoUpdate request
    ) {
        TVideo video = new TVideo();
        video.setId(request.id());
        video.setTitle(request.title().trim());
        return adminVideoService.updateById(video);
    }

    /**
     * 其他接口（根据ID查询、删除）保持不变
     */
    @Operation(summary = "根据ID查询视频", description = "获取视频详情（含时长）")
    @GetMapping("/{id}")
    public TVideo getById(
            @Parameter(description = "视频ID", example = "22") @PathVariable Long id
    ) {
        return adminVideoService.getById(id);
    }

    @Operation(summary = "删除视频", description = "逻辑删除（标记为已删除）")
    @DeleteMapping("/{id}")
    public boolean remove(
            @Parameter(description = "视频ID", example = "22") @PathVariable Long id
    ) {
        return adminVideoService.removeById(id);
    }
}
