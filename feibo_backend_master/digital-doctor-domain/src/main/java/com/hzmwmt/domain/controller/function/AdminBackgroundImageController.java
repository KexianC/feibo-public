package com.hzmwmt.domain.controller.function;

// 文件名：AdminBackgroundImageController.java

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.entity.function.TDigitalImage;
import com.hzmwmt.domain.service.file.FileService;
import com.hzmwmt.domain.service.function.TDigitalImageService;
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
@RequestMapping("/api/admin/function/background-image")
@Tag(name = "AdminBackgroundImageController", description = "后台背景图片管理接口（含OSS上传）")
public class AdminBackgroundImageController {

    @Resource(name = "TDigitalImageServiceImpl")
    private TDigitalImageService digitalImageService;
    @Resource
    private FileService fileService;

    /**
     * 分页+模糊查询（按标题）
     */
    @Operation(summary = "分页查询背景图片", description = "支持按标题模糊搜索，自动过滤已删除数据")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码（默认1）", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数（默认10）", example = "10"),
            @Parameter(name = "title", description = "标题模糊搜索（可选）", example = "医美")
    })
    @GetMapping("/page")
    public IPage<TDigitalImage> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate
    ) {
        // 0表示背景图片类型
        return digitalImageService.pageWithLike(pageNum, pageSize, title, 0, startDate, endDate);
    }

    /**
     * 上传背景图片并保存到数据库
     * 直接将图片上传到OSS后，自动保存到数据库中
     */
    @Operation(summary = "上传背景图片并保存", description = "直接上传到OSS并保存到数据库，返回保存后的背景图片对象")
    @PostMapping("/upload-and-save")
    @OperationLog(operationType = "上传", description = "上传背景图片并保存", recordParams = true)
    public TDigitalImage uploadAndSave(
            @Parameter(description = "背景图片文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "标题", example = "用户反馈背景")
            @RequestParam("title") String title,
            @Parameter(description = "排序值", example = "0")
            @RequestParam(defaultValue = "0") Integer sort,
            @Parameter(description = "是否显示（1：是；0：否）", example = "1")
            @RequestParam(defaultValue = "1") Integer isShow
    ) {
        // 创建数字图片实体
        TDigitalImage digitalImage = new TDigitalImage();
        digitalImage.setName(title); // 使用name属性存储标题
        digitalImage.setType(0); // 0表示背景图片类型

        // 使用TDigitalImageService上传并保存
        if (!digitalImageService.addImageWithFile(digitalImage, file)) {
            throw new IllegalStateException("图片保存失败");
        }

        return digitalImage;
    }

    /**
     * 通用OSS图片上传（已注释，建议使用/upload-and-save接口直接保存到数据库）
     */
    /*
    @Operation(summary = "通用图片上传到OSS", description = "支持jpg、jpeg、png、gif、webp格式，返回图片URL")
    @ApiResponse(responseCode = "200", description = "上传成功",
            content = @Content(schema = @Schema(example = "https://xxx.oss-cn-beijing.aliyuncs.com/image/20251016/xxx.jpg")))
    @PostMapping("/upload")
    public String upload(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        // 直接调用FileService的uploadToOss方法
        return fileService.uploadToOss(file, "background-image/");
    }
    */

    /**
     * 根据ID查询
     */
    @Operation(summary = "根据ID查询背景图片", description = "获取单条背景图片详情")
    @GetMapping("/{id:[0-9]+}")
    public TDigitalImage getById(
            @Parameter(description = "背景图片ID", example = "1") @PathVariable Long id
    ) {
        // 添加参数有效性检查
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("参数'id'必须是有效的正整数");
        }
        TDigitalImage image = digitalImageService.getById(id);
        if (image == null || !Integer.valueOf(0).equals(image.getType())) throw new IllegalArgumentException("图片不存在");
        return image;
    }

    /**
     //      * 新增背景图片（需先调用/upload获取indexImage的URL）
     //      */
//     @Operation(summary = "新增背景图片", description = "indexImage字段需传入/upload接口返回的OSS URL")
//     @PostMapping
//     public boolean save(
//             @Parameter(description = "背景图片信息（indexImage为OSS URL）", required = true)
//             @RequestBody TBackgroundImage image
//     ) {
//         return adminBackgroundImageService.save(image);
//     }

    /**
     * 更新背景图片
     */
    @Operation(summary = "更新背景图片", description = "需传入ID，indexImage可通过/upload更新")
    @PutMapping
    @OperationLog(operationType = "更新", description = "更新背景图片", recordParams = true)
    public boolean update(
            @Parameter(description = "背景图片信息（含ID）", required = true)
            @Valid @RequestBody AdminMaterialWriteRequest.NameUpdate request
    ) {
        TDigitalImage existing = digitalImageService.getById(request.id());
        if (existing == null || !Integer.valueOf(0).equals(existing.getType())) return false;
        TDigitalImage image = new TDigitalImage();
        image.setId(request.id());
        image.setName(request.name().trim());
        return digitalImageService.updateById(image);
    }

    /**
     * 删除背景图片（逻辑删除）
     */
    @Operation(summary = "删除背景图片", description = "逻辑删除，设置is_deleted=1")
    @DeleteMapping("/{id:[0-9]+}")
    @OperationLog(operationType = "删除", description = "删除背景图片", recordParams = true)
    public boolean remove(
            @Parameter(description = "背景图片ID", example = "1") @PathVariable Long id
    ) {
        // 添加参数有效性检查
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("参数'id'必须是有效的正整数");
        }
        TDigitalImage image = digitalImageService.getById(id);
        if (image == null || !Integer.valueOf(0).equals(image.getType())) return false;
        return digitalImageService.removeImagesByIds(java.util.Collections.singletonList(id));
    }
}
