package com.hzmwmt.util;

import com.hzmwmt.constant.DocumentExtensionConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 存储目录文档扫描工具。
 */
@Slf4j
@Component
public class DocumentScan {

    @Value("${storage-path}")
    private String storagePath;

    /**
     * 扫描 storage-path 下的全部受支持文件。
     *
     * @return 文件绝对路径列表
     */
    public List<String> scanFiles() {
        List<String> result = new ArrayList<>();
        Path rootPath = Paths.get(storagePath);

        // 需要被跳过的目录
        Set<String> skipDirs = Set.of(
                "#recycle", // 回收站
                "lost+found",
                ".Recycle_bin",
                "docker",
                "百度网盘",
                "高磊电脑桌面文件2026.3.6",
                "焦可怡文件",
                "张家芝电脑文件"
        );

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith("@") || skipDirs.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE; // 跳过目录
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && isSupportedDocument(file)) {
                        result.add(file.toAbsolutePath().normalize().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("扫描失败", e);
            throw new RuntimeException("扫描失败");
        }

        return result;
    }

    /**
     * 判断文件是否属于支持的文档格式。
     */
    private boolean isSupportedDocument(Path path) {
        String fileName = path.getFileName() == null ? null : path.getFileName().toString();
        String extension = resolveExtension(fileName);
        return StringUtils.hasText(extension)
                && DocumentExtensionConstants.ALL_SUPPORTED_EXTENSIONS.contains(extension);
    }

    /**
     * 提取文件后缀。
     */
    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
