package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TMusic;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.multipart.MultipartFile;

@Hidden // 不在Swagger文档中显示
public interface AdminMusicService extends IService<TMusic> {

    // 分页+标题模糊查询
    IPage<TMusic> pageWithLike(Integer pageNum, Integer pageSize, String title);

    // 通用OSS音乐上传（返回文件URL）
    String uploadMusicToOss(MultipartFile file);

    // 新增音乐并上传文件
    boolean saveWithFileUpload(TMusic music, MultipartFile file);
}
