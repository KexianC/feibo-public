package com.hzmwmt.a_publish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.a_publish.model.dto.ArticleAuditRequest;
import com.hzmwmt.a_publish.model.dto.ArticleGenerateRequest;
import com.hzmwmt.a_publish.model.dto.ArticlePublishRequest;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.common.response.ClientResult;

public interface ArticleManagementService {
    ArticleInfo generateArticle(ArticleGenerateRequest request);
    void addArticle(ArticleInfo articleInfo);
    void editArticle(ArticleInfo articleInfo);
    void deleteArticle(Long id);
    void auditArticle(ArticleAuditRequest request);
    ClientResult publishArticle(ArticlePublishRequest request);
    IPage<ArticleInfo> page(Page<ArticleInfo> page, String title, Integer agentType, Long hospitalId,
                            Long departmentId, Long doctorId, Integer status);
}
