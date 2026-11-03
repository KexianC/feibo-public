package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.ai.rag.service.RagKnowledgeBaseService;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.DocumentExtensionConstants;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.service.KnowledgeDocumentService;
import com.hzmwmt.util.FileSecurityUtils;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private KnowledgeDocumentMapper documentManagementMapper;

    @Resource
    private AppSecurityProperties securityProperties;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    @Resource
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Resource(name = "customThreadPool")
    private ThreadPoolTaskExecutor taskExecutor;

    @Value("${storage-path}")
    private String uploadDir;

    @Override
    public KnowledgeDocument uploadDocument(MultipartFile file, Integer agentType, Long hospitalId, Long departmentId,
                                            Long doctorId, String remark) {
        FileSecurityUtils.validateUpload(file, DocumentExtensionConstants.ALL_SUPPORTED_EXTENSIONS, securityProperties.getMaxUploadBytes());
        KnowledgeAgentScope scope = scopeResolver.resolve(agentType, hospitalId, departmentId, doctorId);

        Long docId = snowflakeIdGenerator.nextId();
        String safeOriginalFilename = FileSecurityUtils.cleanOriginalFilename(file);
        String storedFileName = FileSecurityUtils.storedFileName(safeOriginalFilename);
        Path filePath = FileSecurityUtils.resolveUnderBase(uploadDir, storedFileName);

        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("document upload failed: documentId={}, exceptionType={}",
                    docId, e.getClass().getSimpleName());
            throw new AppException("DOCUMENT_UPLOAD_FAILED", "Document upload failed.");
        }

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(docId)
                .agentType(scope.getAgentType())
                .hospitalId(scope.getHospitalId())
                .hospitalName(scope.getHospitalName())
                .departmentId(scope.getDepartmentId())
                .departmentName(scope.getDepartmentName())
                .doctorId(scope.getDoctorId())
                .doctorName(scope.getDoctorName())
                .fileName(safeOriginalFilename)
                .sourceType(1)
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .chunkCount(0)
                .status(0)
                .remark(remark)
                .build();

        int rows = documentManagementMapper.insert(doc);
        if (rows != 1) {
            log.error("document record insert failed: documentId={}", docId);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.error("document rollback delete failed: documentId={}, exceptionType={}",
                        docId, e.getClass().getSimpleName());
            }
            throw new AppException("DOCUMENT_UPLOAD_FAILED", "Document record insert failed.");
        }
        submitAutoConvert(doc.getId());
        return doc;
    }

    @Override
    public void deleteDocument(Long id) {
        KnowledgeDocument doc = documentManagementMapper.selectById(id);
        if (doc == null) {
            throw new AppException("DOCUMENT_NOT_FOUND", "Document not found.");
        }

        deletePhysicalFile(doc);

        int rows = documentManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new AppException("DOCUMENT_DELETE_FAILED", "Document record delete failed.");
        }
    }

    @Override
    public IPage<KnowledgeDocument> page(Page<KnowledgeDocument> page, Integer agentType, Long hospitalId,
                                         Long departmentId, Long doctorId, String fileName, Integer status) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentType != null, KnowledgeDocument::getAgentType, agentType);
        wrapper.eq(hospitalId != null, KnowledgeDocument::getHospitalId, hospitalId);
        wrapper.eq(departmentId != null, KnowledgeDocument::getDepartmentId, departmentId);
        wrapper.eq(doctorId != null, KnowledgeDocument::getDoctorId, doctorId);
        wrapper.like(fileName != null && !fileName.isEmpty(), KnowledgeDocument::getFileName, fileName);
        wrapper.eq(status != null, KnowledgeDocument::getStatus, status);
        wrapper.orderByDesc(KnowledgeDocument::getCreatedTime);
        return documentManagementMapper.selectPage(page, wrapper);
    }

    private void deletePhysicalFile(KnowledgeDocument doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return;
        }
        Path filePath = Path.of(doc.getFilePath());
        FileSecurityUtils.assertPathInsideBase(uploadDir, filePath);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("document physical delete failed: documentId={}, exceptionType={}",
                    doc.getId(), e.getClass().getSimpleName());
            throw new AppException("DOCUMENT_DELETE_FAILED", "Document file delete failed.");
        }
    }

    private void submitAutoConvert(Long documentId) {
        taskExecutor.execute(() -> {
            try {
                log.info("auto document conversion submitted: documentId={}", documentId);
                ragKnowledgeBaseService.convertDocument(documentId);
                log.info("auto document conversion finished: documentId={}", documentId);
            } catch (AppException e) {
                log.warn("auto document conversion failed: documentId={}, code={}, exceptionType={}",
                        documentId, e.getCode(), e.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("auto document conversion failed unexpectedly: documentId={}, exceptionType={}",
                        documentId, e.getClass().getSimpleName());
            }
        });
    }
}
