package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TDigitalMusic;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.multipart.MultipartFile;

@Hidden // 不在Swagger文档中显示
public interface AdminDigitalMusicService extends IService<TDigitalMusic> {

    // 分页+标题模糊查询
    IPage<TDigitalMusic> pageWithLike(Integer pageNum, Integer pageSize, String name);

    // 通用OSS音乐上传（返回文件URL）
    String uploadMusicToOss(MultipartFile file);

    // 新增音乐并上传文件
    boolean saveWithFileUpload(TDigitalMusic digitalMusic, MultipartFile file);
}