package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;

@Hidden // Swagger 隐藏 Service 接口（仅展示 Controller）
public interface AdminCopywritingCategoryService extends IService<TCopywritingCategory> {

    // 继承 IService 后，已包含基础 CRUD 方法（save、update、remove、getById、list 等）
    // 如需自定义方法，在此声明（如分页查询）

    /**
     * 分页查询文案分类
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<TCopywritingCategory> pageCategories(
            @Parameter(description = "页码", example = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10") Integer pageSize
    );
}
