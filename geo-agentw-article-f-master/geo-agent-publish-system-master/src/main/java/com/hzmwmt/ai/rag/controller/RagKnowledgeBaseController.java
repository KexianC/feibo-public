package com.hzmwmt.ai.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.model.dto.KnowledgeIngestResponse;
import com.hzmwmt.ai.rag.model.dto.KnowledgeSearchHit;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.ai.rag.service.RagKnowledgeBaseService;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.model.entity.KnowledgeDocument;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理接口（上传与向量化已解耦）。
 */
@RestController
@RequestMapping("/rag")
public class RagKnowledgeBaseController {

    @Resource
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    /**
     * 文档向量化转化。
     */
    @PostMapping("/documents/convert")
    public Result<KnowledgeIngestResponse> convertDocument(@RequestParam("documentId") Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new AppException("PARAM_INVALID", "文档ID不合法");
        }
        return Result.success(ragKnowledgeBaseService.convertDocument(documentId));
    }

    /**
     * 删除文档向量及记录。
     */
    @DeleteMapping("/documents/vector")
    public Result<Void> deleteDocumentVector(@RequestParam("documentId") Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new AppException("PARAM_INVALID", "文档ID不合法");
        }
        ragKnowledgeBaseService.deleteDocumentVector(documentId);
        return Result.success();
    }

    /**
     * 向量检索。
     */
    @GetMapping("/search")
    public Result<List<KnowledgeSearchHit>> search(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId) {
        KnowledgeAgentScope scope = scopeResolver.resolve(agentType, hospitalId, departmentId, doctorId);
        return Result.success(ragKnowledgeBaseService.search(query, limit, scope));
    }

    /**
     * 分页查询知识库文档列表。
     */
    @GetMapping("/documents/page")
    public PageResult<KnowledgeDocument> pageDocuments(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "sourceType", required = false) Integer sourceType,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("PARAM_INVALID", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new AppException("PARAM_INVALID", "分页大小必须大于0");
        }

        Page<KnowledgeDocument> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<KnowledgeDocument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(org.springframework.util.StringUtils.hasText(fileName), KnowledgeDocument::getFileName, fileName);
        queryWrapper.eq(status != null, KnowledgeDocument::getStatus, status);
        queryWrapper.eq(sourceType != null, KnowledgeDocument::getSourceType, sourceType);
        queryWrapper.eq(agentType != null, KnowledgeDocument::getAgentType, agentType);
        queryWrapper.eq(hospitalId != null, KnowledgeDocument::getHospitalId, hospitalId);
        queryWrapper.eq(departmentId != null, KnowledgeDocument::getDepartmentId, departmentId);
        queryWrapper.eq(doctorId != null, KnowledgeDocument::getDoctorId, doctorId);
        queryWrapper.orderByDesc(KnowledgeDocument::getCreatedTime);

        return PageResult.of(knowledgeDocumentMapper.selectPage(page, queryWrapper));
    }
}
