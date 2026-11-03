package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.entity.function.TMusic;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.mapper.function.AdminMusicMapper;
import com.hzmwmt.domain.service.function.AdminMusicService;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AdminMusicServiceImpl extends ServiceImpl<AdminMusicMapper, TMusic> implements AdminMusicService {

    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;

    /**
     * 分页+标题模糊查询（自动过滤已删除数据）
     */
    @Override
    public IPage<TMusic> pageWithLike(Integer pageNum, Integer pageSize, String title) {
        Page<TMusic> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TMusic> wrapper = new QueryWrapper<>();
        // 逻辑删除字段由MP自动过滤（@TableLogic生效）
        if (title != null && !title.isEmpty()) {
            wrapper.like("title", title); // 标题模糊匹配
        }
        return baseMapper.selectPage(page, wrapper);
    }

    /**
     * 上传音乐文件到MinIO（通用方法，支持mp3、wav等格式）
     */
    @Override
    public String uploadMusicToOss(MultipartFile file) {
        // 1. 校验文件
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.matches(".*\\.(?i:mp3|wav|flac|aac|m4a)$")) {
            throw new RuntimeException("仅支持mp3、wav、flac、aac、m4a格式");
        }

        // 2. 生成存储路径（示例：music/20251017/xxx.mp3）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = "music/" + dateDir + "/" + uuid + fileExt; // 按日期+UUID存储，避免重复

        // 3. 上传到oss
        return fileStorageGateway.upload(UploadDTO.builder()
                .file(file)
                .path(objectName)
                .build());
    }

    /**
     * 新增音乐并上传文件到OSS
     */
    @Override
    @OperationLog(operationType = "新增", description = "新增音乐并上传文件")
    public boolean saveWithFileUpload(TMusic music, MultipartFile file) {
        // 1. 上传文件到OSS并获取URL
        String musicUrl = uploadMusicToOss(file);

        // 2. 设置音乐URL和其他默认值
        music.setMusicUrl(musicUrl);
        music.setIsDeleted(0); // 设置未删除状态

        // 3. 保存音乐信息到数据库
        return this.save(music);
    }


}


