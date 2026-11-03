package com.hzmwmt.a_publish.controller;

import com.hzmwmt.a_publish.service.PublishArticleClient;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.ClientResult;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.util.FileSecurityUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/file")
public class FileManagementController {

    private static final Set<String> ALLOWED_ASSET_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    @Resource
    private PublishArticleClient publishArticleClient;

    @Resource
    private AppSecurityProperties securityProperties;

    @PostMapping("/uploadSave")
    public ClientResult uploadSave(@RequestPart("file") MultipartFile file) {
        FileSecurityUtils.validateUpload(file, ALLOWED_ASSET_EXTENSIONS, securityProperties.getMaxUploadBytes());
        return publishArticleClient.uploadSave(file);
    }

    @GetMapping("/getFiles")
    public ClientResult getFiles() {
        return publishArticleClient.getFiles();
    }

    @DeleteMapping("/deleteFile")
    public ClientResult deleteFile(@RequestParam("id") Integer id) {
        if (id == null || id <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "Invalid file id.");
        }
        return publishArticleClient.deleteFile(id);
    }
}
