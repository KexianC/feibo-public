package com.hzmwmt.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地文件 MultipartFile 适配器。
 * 用于将磁盘文件复用到现有上传导入流程中。
 */
public class LocalFileMultipartFile implements MultipartFile {

    /**
     * 本地文件路径。
     */
    private final Path path;

    public LocalFileMultipartFile(Path path) {
        this.path = path;
    }

    /**
     * 返回本地文件路径。
     */
    public Path getPath() {
        return path;
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
    }

    @Override
    public String getOriginalFilename() {
        return path.getFileName().toString();
    }

    @Override
    public String getContentType() {
        try {
            return Files.probeContentType(path);
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return Files.size(path) <= 0;
        } catch (IOException exception) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.copy(path, dest.toPath());
    }

    @Override
    public org.springframework.core.io.Resource getResource() {
        return new org.springframework.core.io.FileSystemResource(path);
    }
}
