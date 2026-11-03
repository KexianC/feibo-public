package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.DocumentExtensionConstants;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.service.KnowledgeDocumentService;
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

@RestController
@RequestMapping("/document")
public class KnowledgeDocumentController {

    @Resource
    private KnowledgeDocumentService documentManagementService;

    @Resource
    private AppSecurityProperties securityProperties;

    @PostMapping("/upload")
    public Result<KnowledgeDocument> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "remark", required = false) String remark) {
        FileSecurityUtils.validateUpload(file, DocumentExtensionConstants.ALL_SUPPORTED_EXTENSIONS, securityProperties.getMaxUploadBytes());
        return Result.success(documentManagementService.uploadDocument(file, agentType, hospitalId, departmentId, doctorId, remark));
    }

    @DeleteMapping("/deleteDocument")
    public Result<Void> deleteDocument(@RequestParam("id") Long id) {
        if (id == null || id <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "Invalid document id.");
        }
        documentManagementService.deleteDocument(id);
        return Result.success();
    }

    @GetMapping("/page")
    public PageResult<KnowledgeDocument> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "status", required = false) Integer status) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "Invalid page number.");
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new AppException("ILLEGAL_ARGUMENT", "Invalid page size.");
        }
        Page<KnowledgeDocument> page = new Page<>(pageNum, pageSize);
        IPage<KnowledgeDocument> result = documentManagementService.page(page, agentType, hospitalId, departmentId,
                doctorId, fileName, status);
        return PageResult.of(result);
    }
}
