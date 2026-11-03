package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TDigitalImage;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

@Hidden // 不在Swagger文档显示
public interface TDigitalImageService extends IService<TDigitalImage> {

    /**
     * 分页查询数字图片列表（支持标题模糊搜索和类型筛选）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param title    标题（可选）
     * @param type     图片类型（0:背景图片, 1:插图）
     * @return 分页结果
     */
    IPage<TDigitalImage> pageWithLike(Integer pageNum, Integer pageSize, String title, Integer type);

    IPage<TDigitalImage> pageWithLike(Integer pageNum, Integer pageSize, String title, Integer type, java.time.LocalDate startDate, java.time.LocalDate endDate);

    /**
     * 上传图片到OSS并返回链接
     *
     * @param file 上传的文件
     * @param type 图片类型
     * @return 图片URL
     */
    String uploadImage(MultipartFile file, Integer type);

    /**
     * 上传图片并保存数字图片信息
     *
     * @param digitalImage 数字图片实体
     * @param file         上传的文件
     * @return 是否成功
     */
    boolean addImageWithFile(TDigitalImage digitalImage, MultipartFile file);

    /**
     * 批量删除图片（同时删除OSS中的图片）
     *
     * @param ids 图片ID列表
     * @return 是否成功
     */
    boolean removeImagesByIds(Iterable<? extends Serializable> ids);
}