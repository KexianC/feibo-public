package com.hzmwmt.a_publish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.a_publish.mapper.ArticleManagementMapper;
import com.hzmwmt.a_publish.model.dto.ArticleAuditRequest;
import com.hzmwmt.a_publish.model.dto.ArticleGenerateRequest;
import com.hzmwmt.a_publish.model.dto.ArticlePublishRequest;
import com.hzmwmt.a_publish.model.dto.PostArticleDTO;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.a_publish.service.ArticleManagementService;
import com.hzmwmt.a_publish.service.PublishArticleClient;
import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.ai.rag.service.RagChatService;
import com.hzmwmt.ai.rag.service.KnowledgeAgentScopeResolver;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.ClientResult;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.geo.service.MedicalComplianceService;
import com.hzmwmt.mapper.DoctorManagementMapper;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleManagementServiceImpl implements ArticleManagementService {

    private static final String DEFAULT_MEDICAL_DISCLAIMER =
            "本文仅用于健康科普，不能替代医生面诊、诊断或治疗建议。如有不适，请及时到正规医疗机构就诊。";

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ArticleManagementMapper articleManagementMapper;

    @Resource
    private RagChatService ragChatService;

    @Resource
    private PublishArticleClient publishArticleClient;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MedicalComplianceService medicalComplianceService;

    @Resource
    private KnowledgeAgentScopeResolver scopeResolver;

    @Resource
    private DoctorManagementMapper doctorManagementMapper;

    @Override
    public ArticleInfo generateArticle(ArticleGenerateRequest request) {
        KnowledgeAgentScope scope = resolveScope(request.getAgentType(), request.getHospitalId(),
                request.getDepartmentId(), request.getDoctorId());
        String responseText = ragChatService.chat(request.getPrompt(), scope)
                .collectList()
                .map(parts -> String.join("", parts))
                .block();

        GeneratedArticle generatedArticle = parseGeneratedArticle(responseText);
        ArticleInfo articleInfo = new ArticleInfo();
        applyScope(articleInfo, scope);
        articleInfo.setTitle(generatedArticle.title());
        articleInfo.setContent(generatedArticle.content());
        articleInfo.setCoverUrl(request.getCoverUrl());
        articleInfo.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        articleInfo.setSources(generatedArticle.sources());
        articleInfo.setCitations(generatedArticle.citations());
        articleInfo.setMedicalDisclaimer(generatedArticle.medicalDisclaimer());
        articleInfo.setSchemaJson(generatedArticle.schemaJson());
        addArticle(articleInfo);
        return articleManagementMapper.selectById(articleInfo.getId());
    }

    @Override
    public void addArticle(ArticleInfo articleInfo) {
        articleInfo.setId(snowflakeIdGenerator.nextId());
        applyScope(articleInfo, resolveArticleScope(articleInfo));
        articleInfo.setContent(sanitizeArticleContent(articleInfo.getContent()));
        if (articleInfo.getStatus() == null) {
            articleInfo.setStatus(0);
        }
        if (articleInfo.getWordCount() == null) {
            articleInfo.setWordCount(articleInfo.getContent() != null ? articleInfo.getContent().length() : 0);
        }
        enrichGeoFields(articleInfo);
        assertStatusAllowed(articleInfo);
        int rows = articleManagementMapper.insert(articleInfo);
        if (rows != 1) {
            throw new RuntimeException("新增文章失败");
        }
    }

    @Override
    public void editArticle(ArticleInfo articleInfo) {
        ArticleInfo current = articleManagementMapper.selectById(articleInfo.getId());
        if (current == null) {
            throw new AppException("ARTICLE_NOT_FOUND", "文章不存在", HttpStatus.NOT_FOUND);
        }

        ArticleInfo updateEntity = new ArticleInfo();
        updateEntity.setId(articleInfo.getId());
        KnowledgeAgentScope targetScope = null;
        if (hasScope(articleInfo)) {
            targetScope = resolveArticleScope(articleInfo);
            applyScope(updateEntity, targetScope);
        }
        if (StringUtils.hasText(articleInfo.getTitle())) {
            updateEntity.setTitle(articleInfo.getTitle());
        }
        if (StringUtils.hasText(articleInfo.getContent())) {
            String sanitizedContent = sanitizeArticleContent(articleInfo.getContent());
            updateEntity.setContent(sanitizedContent);
            updateEntity.setWordCount(sanitizedContent.length());
        }
        if (articleInfo.getCoverUrl() != null) {
            updateEntity.setCoverUrl(articleInfo.getCoverUrl());
        }
        if (articleInfo.getStatus() != null) {
            updateEntity.setStatus(articleInfo.getStatus());
        }
        if (StringUtils.hasText(articleInfo.getRemark())) {
            updateEntity.setRemark(articleInfo.getRemark());
        }
        if (StringUtils.hasText(articleInfo.getSources())) {
            updateEntity.setSources(articleInfo.getSources());
        }
        if (StringUtils.hasText(articleInfo.getCitations())) {
            updateEntity.setCitations(articleInfo.getCitations());
        }
        if (StringUtils.hasText(articleInfo.getMedicalDisclaimer())) {
            updateEntity.setMedicalDisclaimer(articleInfo.getMedicalDisclaimer());
        }
        if (StringUtils.hasText(articleInfo.getSchemaJson())) {
            updateEntity.setSchemaJson(articleInfo.getSchemaJson());
        }

        String auditTitle = StringUtils.hasText(updateEntity.getTitle()) ? updateEntity.getTitle() : current.getTitle();
        String auditContent = StringUtils.hasText(updateEntity.getContent()) ? updateEntity.getContent() : current.getContent();
        updateEntity.setStatusAudit(medicalComplianceService.auditToJson(auditTitle, auditContent));
        Integer targetStatus = updateEntity.getStatus() == null ? current.getStatus() : updateEntity.getStatus();
        if (targetStatus != null && targetStatus != 0) {
            medicalComplianceService.assertPublishable(ArticleInfo.builder()
                    .title(auditTitle)
                    .content(auditContent)
                    .build());
        }

        int rows = articleManagementMapper.updateById(updateEntity);
        if (rows != 1) {
            throw new RuntimeException("更新文章失败");
        }
        updateScopeFields(articleInfo.getId(), targetScope);
    }

    @Override
    public void deleteArticle(Long id) {
        int rows = articleManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new RuntimeException("删除文章失败");
        }
    }

    @Override
    public IPage<ArticleInfo> page(Page<ArticleInfo> page, String title, Integer agentType, Long hospitalId,
                                   Long departmentId, Long doctorId, Integer status) {
        LambdaQueryWrapper<ArticleInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(title), ArticleInfo::getTitle, title);
        queryWrapper.eq(agentType != null, ArticleInfo::getAgentType, agentType);
        queryWrapper.eq(hospitalId != null, ArticleInfo::getHospitalId, hospitalId);
        queryWrapper.eq(departmentId != null, ArticleInfo::getDepartmentId, departmentId);
        queryWrapper.eq(doctorId != null, ArticleInfo::getDoctorId, doctorId);
        queryWrapper.eq(status != null, ArticleInfo::getStatus, status);
        queryWrapper.orderByDesc(ArticleInfo::getCreatedTime);
        return articleManagementMapper.selectPage(page, queryWrapper);
    }

    @Override
    public void auditArticle(ArticleAuditRequest request) {
        ArticleInfo articleInfo = articleManagementMapper.selectById(request.getArticleId());
        if (articleInfo == null) {
            throw new AppException("ARTICLE_NOT_FOUND", "文章不存在", HttpStatus.NOT_FOUND);
        }
        if (request.getStatus() == null || (request.getStatus() != 2 && request.getStatus() != 4)) {
            throw new AppException("ILLEGAL_ARGUMENT", "审核状态只能为通过或不通过");
        }
        if (request.getStatus() == 4 && !StringUtils.hasText(request.getReason())) {
            throw new AppException("ILLEGAL_ARGUMENT", "审核不通过时必须填写原因");
        }
        if (request.getStatus() == 2) {
            medicalComplianceService.assertPublishable(articleInfo);
        }

        ArticleInfo update = new ArticleInfo();
        update.setId(request.getArticleId());
        update.setStatus(request.getStatus());
        update.setStatusAudit(medicalComplianceService.auditToJson(articleInfo.getTitle(), articleInfo.getContent()));
        if (StringUtils.hasText(request.getReason())) {
            update.setRemark(request.getReason());
        }
        int rows = articleManagementMapper.updateById(update);
        if (rows != 1) {
            throw new RuntimeException("审核文章失败");
        }
    }

    @Override
    public ClientResult publishArticle(ArticlePublishRequest request) {
        ArticleInfo articleInfo = articleManagementMapper.selectById(request.getArticleId());
        if (articleInfo == null) {
            throw new AppException("ARTICLE_NOT_FOUND", "文章不存在", HttpStatus.NOT_FOUND);
        }
        if (articleInfo.getStatus() == null || articleInfo.getStatus() != 2) {
            throw new AppException("ARTICLE_NOT_APPROVED", "只有审核通过的文章才能发布");
        }
        if (request.getType() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "发布平台不能为空");
        }
        if (!StringUtils.hasText(request.getCookieFile())) {
            throw new AppException("ILLEGAL_ARGUMENT", "发布账号 Cookie 文件不能为空");
        }
        medicalComplianceService.assertPublishable(articleInfo);

        PostArticleDTO dto = new PostArticleDTO();
        dto.setType(request.getType());
        dto.setTitle(articleInfo.getTitle());
        dto.setContent(articleInfo.getContent());
        dto.setImg(StringUtils.hasText(request.getImg()) ? request.getImg() : articleInfo.getCoverUrl());
        dto.setTags(request.getTags() == null ? List.of() : request.getTags());
        dto.setCookie_file(request.getCookieFile());
        dto.setText(StringUtils.hasText(request.getText()) ? request.getText() : articleInfo.getContent());

        ClientResult result = publishArticleClient.postArticle(dto);
        if (result != null && Integer.valueOf(200).equals(result.getCode())) {
            ArticleInfo update = new ArticleInfo();
            update.setId(articleInfo.getId());
            update.setStatus(3);
            update.setStatusAudit(medicalComplianceService.auditToJson(articleInfo.getTitle(), articleInfo.getContent()));
            if (StringUtils.hasText(request.getRemark())) {
                update.setRemark(request.getRemark());
            }
            articleManagementMapper.updateById(update);
        }
        return result;
    }

    private GeneratedArticle parseGeneratedArticle(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            throw new AppException("GENERATE_EMPTY", "AI未返回有效文章内容");
        }
        String normalized = normalizeJson(responseText);
        try {
            JsonNode root = objectMapper.readTree(normalized);
            String title = root.path("title").asText("");
            String content = root.path("content").asText("");
            if (StringUtils.hasText(title) && StringUtils.hasText(content)) {
                return new GeneratedArticle(
                        title,
                        sanitizeArticleContent(content),
                        optionalJson(root, "sources", "[]"),
                        optionalJson(root, "citations", "[]"),
                        root.path("medicalDisclaimer").asText(DEFAULT_MEDICAL_DISCLAIMER),
                        optionalJson(root, "schemaJson", null)
                );
            }
        } catch (Exception ignored) {
            // Plain text fallback.
        }

        String fallbackTitle = normalized.lines()
                .filter(StringUtils::hasText)
                .findFirst()
                .map(line -> line.replaceFirst("^#+\\s*", "").trim())
                .orElse("AI生成文章");
        if (fallbackTitle.length() > 80) {
            fallbackTitle = fallbackTitle.substring(0, 80);
        }
        return new GeneratedArticle(fallbackTitle, sanitizeArticleContent(normalized), "[]", "[]", DEFAULT_MEDICAL_DISCLAIMER, null);
    }

    private String sanitizeArticleContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        return content
                .replaceAll("(?m)^\\s*(参考文章|参考资料|参考来源|引用来源)\\s*[:：].*$", "")
                .replaceAll("[（(][^（）()\\n]*(?i:\\.(?:md|pdf|docx?|xlsx?))[^（）()\\n]*[）)]", "")
                .replaceAll("[\\[【][^\\[\\]【】\\n]*(?i:\\.(?:md|pdf|docx?|xlsx?))[^\\[\\]【】\\n]*[\\]】]", "")
                .replaceAll("(?i)\\b[\\w\\-]+\\.(?:md|pdf|docx?|xlsx?)\\b", "")
                .replaceAll("[ \\t]+([，。；：、！？])", "$1")
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void enrichGeoFields(ArticleInfo articleInfo) {
        if (!StringUtils.hasText(articleInfo.getSources())) {
            articleInfo.setSources("[]");
        }
        if (!StringUtils.hasText(articleInfo.getCitations())) {
            articleInfo.setCitations("[]");
        }
        if (!StringUtils.hasText(articleInfo.getMedicalDisclaimer())) {
            articleInfo.setMedicalDisclaimer(DEFAULT_MEDICAL_DISCLAIMER);
        }
        if (!StringUtils.hasText(articleInfo.getSchemaJson())) {
            articleInfo.setSchemaJson(buildSchemaJson(articleInfo));
        }
        articleInfo.setStatusAudit(medicalComplianceService.auditToJson(articleInfo.getTitle(), articleInfo.getContent()));
    }

    private void assertStatusAllowed(ArticleInfo articleInfo) {
        if (articleInfo.getStatus() != null && articleInfo.getStatus() != 0) {
            medicalComplianceService.assertPublishable(articleInfo);
        }
    }

    private KnowledgeAgentScope resolveScope(Integer agentType, Long hospitalId, Long departmentId, Long doctorId) {
        return scopeResolver.resolve(agentType, hospitalId, departmentId, doctorId);
    }

    private KnowledgeAgentScope resolveArticleScope(ArticleInfo articleInfo) {
        return resolveScope(articleInfo.getAgentType(), articleInfo.getHospitalId(),
                articleInfo.getDepartmentId(), articleInfo.getDoctorId());
    }

    private void applyScope(ArticleInfo articleInfo, KnowledgeAgentScope scope) {
        articleInfo.setAgentType(scope.getAgentType());
        articleInfo.setHospitalId(scope.getHospitalId());
        articleInfo.setDepartmentId(scope.getDepartmentId());
        articleInfo.setDoctorId(scope.getDoctorId());
    }

    private void updateScopeFields(Long articleId, KnowledgeAgentScope scope) {
        if (scope == null) {
            return;
        }
        articleManagementMapper.update(null, new LambdaUpdateWrapper<ArticleInfo>()
                .eq(ArticleInfo::getId, articleId)
                .set(ArticleInfo::getAgentType, scope.getAgentType())
                .set(ArticleInfo::getHospitalId, scope.getHospitalId())
                .set(ArticleInfo::getDepartmentId, scope.getDepartmentId())
                .set(ArticleInfo::getDoctorId, scope.getDoctorId()));
    }

    private boolean hasScope(ArticleInfo articleInfo) {
        return articleInfo.getAgentType() != null
                || articleInfo.getHospitalId() != null
                || articleInfo.getDepartmentId() != null
                || articleInfo.getDoctorId() != null;
    }

    private Map<String, Object> buildProvider(KnowledgeAgentScope scope) {
        Map<String, Object> provider = new LinkedHashMap<>();
        if (scope.getAgentType() != null && scope.getAgentType() == KnowledgeAgentTypeConstants.DOCTOR) {
            provider.put("@type", "Person");
            provider.put("name", scope.getDoctorName());
            provider.put("affiliation", scope.getHospitalName() == null ? "" : scope.getHospitalName());
            return provider;
        }
        provider.put("@type", "MedicalOrganization");
        provider.put("name", scope.getHospitalName() == null ? "" : scope.getHospitalName());
        if (scope.getAgentType() != null && scope.getAgentType() == KnowledgeAgentTypeConstants.DEPARTMENT) {
            provider.put("department", scope.getDepartmentName() == null ? "" : scope.getDepartmentName());
        }
        return provider;
    }

    private String buildSchemaJson(ArticleInfo articleInfo) {
        try {
            KnowledgeAgentScope scope = resolveArticleScope(articleInfo);
            DoctorInfo doctor = articleInfo.getDoctorId() == null ? null : doctorManagementMapper.selectById(articleInfo.getDoctorId());
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("@context", "https://schema.org");
            schema.put("@type", "MedicalWebPage");
            schema.put("headline", articleInfo.getTitle());
            schema.put("about", "健康科普");
            schema.put("inLanguage", "zh-CN");
            schema.put("medicalAudience", Map.of("@type", "MedicalAudience", "audienceType", "Patient"));
            schema.put("disclaimer", articleInfo.getMedicalDisclaimer());
            schema.put("provider", buildProvider(scope));
            if (doctor != null && StringUtils.hasText(doctor.getDoctorName())) {
                schema.put("author", Map.of(
                        "@type", "Person",
                        "name", doctor.getDoctorName(),
                        "affiliation", doctor.getHospitalName() == null ? "" : doctor.getHospitalName()
                ));
            }
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String optionalJson(JsonNode root, String fieldName, String fallback) throws Exception {
        JsonNode value = root.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (value.isTextual()) {
            return value.asText(fallback);
        }
        return objectMapper.writeValueAsString(value);
    }

    private String normalizeJson(String responseText) {
        String normalized = responseText.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        return normalized;
    }

    private record GeneratedArticle(String title, String content, String sources, String citations,
                                    String medicalDisclaimer, String schemaJson) {
    }
}
