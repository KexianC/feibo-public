package com.hzmwmt.domain.controller.function;

// 文件名：AdminIllustrationController.java

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
@RequestMapping("/api/admin/function/illustration")
@Tag(name = "AdminIllustrationController", description = "后台插图管理接口")
public class AdminIllustrationController {

    @Resource(name = "TDigitalImageServiceImpl")
    private TDigitalImageService digitalImageService;
    @Resource
    private FileService fileService;

    @Operation(summary = "分页查询插图列表", description = "支持标题模糊搜索")
    @Parameters({
            @Parameter(name = "pageNum", description = "页码，默认1", example = "1"),
            @Parameter(name = "pageSize", description = "每页条数，默认10", example = "10"),
            @Parameter(name = "title", description = "标题模糊搜索（可选）", example = "色块")
    })
    @GetMapping("/page")
    public IPage<TDigitalImage> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title
    ) {
        // 1表示插图类型
        return digitalImageService.pageWithLike(pageNum, pageSize, title, 1);
    }


    @Operation(summary = "新增插图并上传索引图", description = "上传索引图并保存插图信息")
    @PostMapping("/add-with-image")
    @OperationLog(description = "新增插图")
    public TDigitalImage uploadAndSave(
            @Parameter(description = "插图文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "标题", example = "系统插图")
            @RequestParam("title") String title,
            @Parameter(description = "排序值", example = "0")
            @RequestParam(defaultValue = "0") Integer sort,
            @Parameter(description = "是否显示（1：是；0：否）", example = "1")
            @RequestParam(defaultValue = "1") Integer isShow
    ) {
        // 创建数字图片实体
        TDigitalImage digitalImage = new TDigitalImage();
        digitalImage.setName(title); // 使用name属性存储标题
        digitalImage.setType(1); // 1表示插图类型

        // 使用TDigitalImageService上传并保存
        if (!digitalImageService.addImageWithFile(digitalImage, file)) {
            throw new IllegalStateException("图片保存失败");
        }

        return digitalImage;
    }

    @Operation(summary = "根据 ID 查询插图", description = "获取单张插图的详细信息")
    @GetMapping("/{id}")
    public TDigitalImage getById(
            @Parameter(description = "插图ID", example = "1") @PathVariable Long id
    ) {
        // 添加参数有效性检查
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("参数'id'必须是有效的正整数");
        }
        TDigitalImage image = digitalImageService.getById(id);
        if (image == null || !Integer.valueOf(1).equals(image.getType())) throw new IllegalArgumentException("图片不存在");
        return image;
    }

    @Operation(summary = "更新插图", description = "更新插图信息（需包含 ID）")
    @PutMapping
    public boolean update(
            @Parameter(description = "插图信息（含ID）", required = true)
            @Valid @RequestBody AdminMaterialWriteRequest.NameUpdate request
    ) {
        TDigitalImage existing = digitalImageService.getById(request.id());
        if (existing == null || !Integer.valueOf(1).equals(existing.getType())) return false;
        TDigitalImage illustration = new TDigitalImage();
        illustration.setId(request.id());
        illustration.setName(request.name().trim());
        return digitalImageService.updateById(illustration);
    }

    @Operation(summary = "删除插图", description = "逻辑删除插图（标记为已删除）")
    @DeleteMapping("/{id}")
    public boolean remove(
            @Parameter(description = "插图ID", example = "1") @PathVariable Long id
    ) {
        // 添加参数有效性检查
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("参数'id'必须是有效的正整数");
        }
        TDigitalImage image = digitalImageService.getById(id);
        if (image == null || !Integer.valueOf(1).equals(image.getType())) return false;
        return digitalImageService.removeImagesByIds(java.util.Collections.singletonList(id));
    }
}
