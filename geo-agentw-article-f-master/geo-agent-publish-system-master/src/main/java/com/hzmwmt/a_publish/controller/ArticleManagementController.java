package com.hzmwmt.a_publish.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.a_publish.model.dto.ArticleAuditRequest;
import com.hzmwmt.a_publish.model.dto.ArticleGenerateRequest;
import com.hzmwmt.a_publish.model.dto.ArticlePublishRequest;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.a_publish.service.ArticleManagementService;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.ClientResult;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/article")
public class ArticleManagementController {

    @Resource
    private ArticleManagementService articleManagementService;

    @PostMapping("/generate")
    public Result<ArticleInfo> generateArticle(@RequestBody ArticleGenerateRequest request) {
        if (request == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "生成参数不能为空");
        }
        if (!hasAgentScope(request.getAgentType(), request.getHospitalId(), request.getDepartmentId(), request.getDoctorId())) {
            throw new AppException("ILLEGAL_ARGUMENT", "必须选择医院、科室或医生知识库");
        }
        if (StringUtils.isBlank(request.getPrompt())) {
            throw new AppException("ILLEGAL_ARGUMENT", "写作需求不能为空");
        }
        return Result.success(articleManagementService.generateArticle(request));
    }

    @PostMapping("/addArticle")
    public Result<Void> addArticle(@RequestBody ArticleInfo articleInfo) {
        validateArticle(articleInfo);
        articleManagementService.addArticle(articleInfo);
        return Result.success();
    }

    @PostMapping("/editArticle")
    public Result<Void> editArticle(@RequestBody ArticleInfo articleInfo) {
        if (articleInfo == null || articleInfo.getId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章ID不能为空");
        }
        articleManagementService.editArticle(articleInfo);
        return Result.success();
    }

    @PostMapping("/audit")
    public Result<Void> auditArticle(@RequestBody ArticleAuditRequest request) {
        if (request == null || request.getArticleId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章ID不能为空");
        }
        articleManagementService.auditArticle(request);
        return Result.success();
    }

    @PostMapping("/publish")
    public ClientResult publishArticle(@RequestBody ArticlePublishRequest request) {
        if (request == null || request.getArticleId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章ID不能为空");
        }
        return articleManagementService.publishArticle(request);
    }

    @DeleteMapping("/deleteArticle")
    public Result<Void> deleteArticle(@RequestParam("id") Long id) {
        if (id == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章ID不能为空");
        }
        articleManagementService.deleteArticle(id);
        return Result.success();
    }

    @GetMapping("/page")
    public PageResult<ArticleInfo> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "status", required = false) Integer status) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于0");
        }
        Page<ArticleInfo> page = new Page<>(pageNum, pageSize);
        IPage<ArticleInfo> result = articleManagementService.page(page, title, agentType, hospitalId, departmentId,
                doctorId, status);
        return PageResult.of(result);
    }

    private void validateArticle(ArticleInfo articleInfo) {
        if (articleInfo == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章信息不能为空");
        }
        if (StringUtils.isBlank(articleInfo.getTitle())) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章标题不能为空");
        }
        if (StringUtils.isBlank(articleInfo.getContent())) {
            throw new AppException("ILLEGAL_ARGUMENT", "文章内容不能为空");
        }
        if (!hasAgentScope(articleInfo.getAgentType(), articleInfo.getHospitalId(), articleInfo.getDepartmentId(), articleInfo.getDoctorId())) {
            throw new AppException("ILLEGAL_ARGUMENT", "必须选择医院、科室或医生知识库");
        }
    }

    private boolean hasAgentScope(Integer agentType, Long hospitalId, Long departmentId, Long doctorId) {
        return agentType != null || hospitalId != null || departmentId != null || doctorId != null;
    }
}
