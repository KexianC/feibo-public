package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.entity.function.TDigitalImage;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.mapper.function.TDigitalImageMapper;
import com.hzmwmt.domain.service.function.TDigitalImageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class TDigitalImageServiceImpl extends ServiceImpl<TDigitalImageMapper, TDigitalImage>
        implements TDigitalImageService {

    private static final Logger log = Logger.getLogger(TDigitalImageServiceImpl.class.getName());

    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;

    @Override
    public IPage<TDigitalImage> pageWithLike(Integer pageNum, Integer pageSize, String title, Integer type) {
        return pageWithLike(pageNum, pageSize, title, type, null, null);
    }

    @Override
    public IPage<TDigitalImage> pageWithLike(Integer pageNum, Integer pageSize, String title, Integer type, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        Page<TDigitalImage> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TDigitalImage> wrapper = new QueryWrapper<>();

        // 逻辑删除字段自动过滤（MP的@TableLogic生效）
        if (title != null && !title.isEmpty()) {
            wrapper.like("name", title.trim()); // 标题模糊匹配
        }

        // 按类型筛选
        if (type != null) {
            wrapper.eq("type", type); // 0:背景图片, 1:插图
        }

        if (startDate != null) wrapper.ge("create_time", startDate.atStartOfDay());
        if (endDate != null) wrapper.lt("create_time", endDate.plusDays(1).atStartOfDay());
        wrapper.orderByDesc("id");
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public String uploadImage(MultipartFile file, Integer type) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new RuntimeException("文件名不能为空");
        }

        // 根据类型确定存储路径前缀
        String typePrefix = type == 0 ? "background" : "illustration";

        // 生成存储路径（示例：background/20231007/xxx.png 或 illustration/20231007/xxx.png）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = typePrefix + "/" + dateDir + "/" + uuid + fileExt;

        // 上传到 oss
        return fileStorageGateway.upload(UploadDTO.builder()
                .path(objectName)
                .file(file)
                .build());
    }

    @Override
    public boolean addImageWithFile(TDigitalImage digitalImage, MultipartFile file) {
        // 上传图片
        Integer type = digitalImage.getType();
        if (type == null) {
            throw new RuntimeException("图片类型不能为空");
        }
        String imageUrl = uploadImage(file, type);

        // 设置图片URL
        digitalImage.setUrl(imageUrl);

        // 设置digitalImageId为当前时间戳
        digitalImage.setDigitalImageId(System.currentTimeMillis());

        // 保存图片信息
        return save(digitalImage);
    }

    @Override
    public boolean removeImagesByIds(Iterable<? extends Serializable> ids) {
        // 将Iterable转换为List
        List<? extends Serializable> idList = ids instanceof List ? (List<? extends Serializable>) ids : java.util.stream.StreamSupport.stream(ids.spliterator(), false).collect(java.util.stream.Collectors.toList());

        // 获取要删除的图片信息，用于后续删除 oss 中的文件
        List<TDigitalImage> digitalImages = listByIds(idList);

        // 批量删除数据库记录
        boolean deleteSuccess = removeByIds(idList);

        if (deleteSuccess && !digitalImages.isEmpty()) {
            // 删除 oss 中的图片文件
            for (TDigitalImage digitalImage : digitalImages) {
                String url = digitalImage.getUrl();
                fileStorageGateway.delete(url);
            }

        }

        return deleteSuccess;
    }
}