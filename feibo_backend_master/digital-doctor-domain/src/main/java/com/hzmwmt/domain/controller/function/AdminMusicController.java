package com.hzmwmt.domain.controller.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.function.TMusic;
import com.hzmwmt.domain.service.function.AdminMusicService;
import com.hzmwmt.types.annotations.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@Deprecated
@Tag(name = "音乐管理", description = "用于管理员端的音乐上传、增删改查操作")
public class AdminMusicController {

    @Resource
    private AdminMusicService adminMusicService;

    /**
     * 分页+标题模糊查询
     */
    @Operation(summary = "分页查询音乐列表", description = "支持按标题模糊搜索，自动过滤已删除数据")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10"),
            @Parameter(name = "title", description = "标题模糊搜索（可选）", example = "动感时尚")
    })
    @OperationLog(operationType = "查询", description = "分页查询音乐列表")
    @GetMapping("/page")
    public Result<IPage<TMusic>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title
    ) {
        log.info("分页查询音乐列表，标题关键词长度：{}", title == null ? 0 : title.length());
        IPage<TMusic> resultPage = adminMusicService.pageWithLike(pageNum, pageSize, title);
        return Result.success(resultPage);
    }

    /**
     * 上传音乐到OSS（返回URL用于新增/编辑）
     * 已注释：现通过新增音乐接口直接上传到OSS
     */
    /*@Operation(summary = "上传音乐到OSS", description = "支持mp3、wav、flac、aac格式，返回文件URL")
    @ApiResponse(responseCode = "200", description = "上传成功",
            content = @Content(schema = @Schema(example = "https://xxx.oss-cn-beijing.aliyuncs.com/music/20251017/xxx.mp3")))
    @OperationLog(operationType = "上传", description = "上传音乐到OSS")
    @PostMapping("/upload")
    public Result<String> upload(
            @Parameter(description = "音乐文件", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        log.info("上传音乐文件，文件大小：{}", file.getSize());
        String url = adminMusicService.uploadMusicToOss(file);
        return Result.success(url);
    }*/

    /**
     * 根据ID查询音乐详情
     */
    @Operation(summary = "根据ID查询音乐", description = "获取单条音乐信息（含OSS链接）")
    @OperationLog(operationType = "查询", description = "根据ID查询音乐详情")
    @GetMapping("/{id}")
    public Result<TMusic> getById(
            @Parameter(description = "音乐ID", example = "1") @PathVariable Long id
    ) {
        log.info("查询音乐详情，ID：{}", id);
        TMusic music = adminMusicService.getById(id);
        return Result.success(music);
    }

    /**
     * 新增音乐（直接处理文件上传并保存到OSS）
     */
    @Operation(summary = "新增音乐", description = "直接上传音乐文件并保存到OSS，同时保存音乐信息")
    @OperationLog(operationType = "新增", description = "新增音乐")
    @PostMapping
    public Result<Boolean> save(
            @RequestParam("title") String title,
            @RequestParam("sort") Integer sort,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("新增音乐，标题长度：{}", title == null ? 0 : title.length());
        TMusic music = new TMusic();
        music.setTitle(title);
        music.setSort(sort);
        boolean result = adminMusicService.saveWithFileUpload(music, file);
        return Result.success(result);
    }

    /**
     * 更新音乐信息
     */
    @Operation(summary = "更新音乐", description = "需传入ID，musicUrl可通过/upload更新")
    @OperationLog(operationType = "修改", description = "更新音乐信息")
    @PutMapping
    public Result<Boolean> update(
            @Parameter(description = "音乐信息（含ID）", required = true)
            @RequestBody TMusic music
    ) {
        log.info("更新音乐，ID：{}", music.getId());
        boolean result = adminMusicService.updateById(music);
        return Result.success(result);
    }

    /**
     * 逻辑删除音乐
     */
    @Operation(summary = "删除音乐", description = "逻辑删除（设置is_deleted=1）")
    @OperationLog(operationType = "删除", description = "逻辑删除音乐")
    @DeleteMapping("/{id}")
    public Result<Boolean> remove(
            @Parameter(description = "音乐ID", example = "1") @PathVariable Long id
    ) {
        log.info("删除音乐，ID：{}", id);
        boolean result = adminMusicService.removeById(id);
        return Result.success(result);
    }
}
