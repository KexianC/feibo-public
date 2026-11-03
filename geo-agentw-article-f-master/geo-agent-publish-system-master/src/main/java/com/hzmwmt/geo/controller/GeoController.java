package com.hzmwmt.geo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.a_publish.mapper.ArticleManagementMapper;
import com.hzmwmt.a_publish.model.entity.ArticleInfo;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.geo.model.dto.ComplianceAuditResult;
import com.hzmwmt.geo.model.dto.GeoFeedbackGenerateRequest;
import com.hzmwmt.geo.model.dto.GeoFeedbackHandlingRequest;
import com.hzmwmt.geo.model.dto.GeoFeedbackPublicationRequest;
import com.hzmwmt.geo.service.GeoFeedbackWorkflowService;
import com.hzmwmt.geo.model.dto.GeoMonitorRunRequest;
import com.hzmwmt.geo.model.dto.GeoProbeRunRequest;
import com.hzmwmt.geo.model.dto.GeoQueryGenerateRequest;
import com.hzmwmt.geo.model.dto.GeoSiteRecommendationStatusRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorCreateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorStatusResponse;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorUpdateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteReportResponse;
import com.hzmwmt.geo.model.dto.MedicalComplianceAuditRequest;
import com.hzmwmt.geo.model.entity.GeoFeedbackSuggestion;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoMonitorTask;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSiteRecommendation;
import com.hzmwmt.geo.model.entity.GeoSourceAsset;
import com.hzmwmt.geo.model.entity.GeoWebsiteCheck;
import com.hzmwmt.geo.model.entity.GeoWebsiteMonitor;
import com.hzmwmt.geo.service.GeoDialogueProbeService;
import com.hzmwmt.geo.service.GeoFeedbackService;
import com.hzmwmt.geo.service.GeoMonitorService;
import com.hzmwmt.geo.service.GeoQueryService;
import com.hzmwmt.geo.service.GeoSiteRecommendationService;
import com.hzmwmt.geo.service.GeoSourceAssetService;
import com.hzmwmt.geo.service.GeoWebsiteMonitorService;
import com.hzmwmt.geo.service.MedicalComplianceService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geo")
public class GeoController {

    @Resource
    private GeoQueryService geoQueryService;

    @Resource
    private GeoMonitorService geoMonitorService;

    @Resource
    private GeoFeedbackService geoFeedbackService;

    @Resource
    private GeoFeedbackWorkflowService feedbackWorkflow;

    @GetMapping("/feedback/{id}")
    public Result<GeoFeedbackSuggestion> feedbackDetail(@PathVariable("id") Long id) {
        return Result.success(feedbackWorkflow.detail(id));
    }

    @PutMapping("/feedback/{id}/handling")
    public Result<GeoFeedbackSuggestion> updateFeedbackHandling(@PathVariable("id") Long id,
            @RequestBody GeoFeedbackHandlingRequest request) {
        return Result.success(feedbackWorkflow.updateHandling(id, request));
    }

    @PostMapping("/feedback/{id}/external-publication")
    public Result<GeoFeedbackSuggestion> confirmFeedbackPublication(@PathVariable("id") Long id,
            @RequestBody GeoFeedbackPublicationRequest request) {
        return Result.success(feedbackWorkflow.confirmExternalPublication(id, request));
    }

    @PostMapping("/feedback/{id}/retest")
    public Result<GeoFeedbackSuggestion> retestFeedback(@PathVariable("id") Long id) {
        return Result.success(feedbackWorkflow.requestRetest(id));
    }

    @Resource
    private MedicalComplianceService medicalComplianceService;

    @Resource
    private GeoDialogueProbeService dialogueProbeService;

    @Resource
    private GeoSourceAssetService sourceAssetService;

    @Resource
    private GeoSiteRecommendationService siteRecommendationService;

    @Resource
    private GeoWebsiteMonitorService websiteMonitorService;

    @Resource
    private ArticleManagementMapper articleManagementMapper;

    @PostMapping("/queries/generate")
    public Result<List<GeoQuery>> generateQueries(@RequestBody GeoQueryGenerateRequest request) {
        return Result.success(geoQueryService.generateQueries(request));
    }

    @PostMapping("/queries")
    public Result<GeoQuery> addManualQuery(@RequestBody GeoQuery query) {
        return Result.success(geoQueryService.addManualQuery(query));
    }

    @PutMapping("/queries/{id}")
    public Result<GeoQuery> updateQuery(@PathVariable("id") Long id, @RequestBody GeoQuery query) {
        return Result.success(geoQueryService.updateQuery(id, query));
    }

    @PostMapping("/websites")
    public Result<GeoWebsiteMonitorStatusResponse> createWebsiteMonitor(
            @RequestBody GeoWebsiteMonitorCreateRequest request) {
        return Result.success(websiteMonitorService.create(request));
    }

    @GetMapping("/websites/page")
    public PageResult<GeoWebsiteMonitorStatusResponse> pageWebsiteMonitors(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "active", required = false) Integer active,
            @RequestParam(value = "monitorEnabled", required = false) Integer monitorEnabled,
            @RequestParam(value = "lastCheckStatus", required = false) Integer lastCheckStatus,
            @RequestParam(value = "domain", required = false) String domain) {
        assertPage(pageNum, pageSize);
        IPage<GeoWebsiteMonitorStatusResponse> result = websiteMonitorService.page(
                new Page<GeoWebsiteMonitor>(pageNum, pageSize),
                hospitalId, active, monitorEnabled, lastCheckStatus, domain
        );
        return PageResult.of(result);
    }

    @PutMapping("/websites/{id}")
    public Result<GeoWebsiteMonitorStatusResponse> updateWebsiteMonitor(
            @PathVariable("id") Long id,
            @RequestBody GeoWebsiteMonitorUpdateRequest request) {
        return Result.success(websiteMonitorService.update(id, request));
    }

    @PostMapping("/websites/{id}/checks")
    public Result<GeoWebsiteCheck> runWebsiteCheck(@PathVariable("id") Long id) {
        return Result.success(websiteMonitorService.runCheck(id));
    }

    @GetMapping("/websites/{id}/checks/page")
    public PageResult<GeoWebsiteCheck> pageWebsiteChecks(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status) {
        assertPage(pageNum, pageSize);
        IPage<GeoWebsiteCheck> result = websiteMonitorService.checkPage(
                new Page<GeoWebsiteCheck>(pageNum, pageSize), id, status
        );
        return PageResult.of(result);
    }

    @GetMapping("/websites/{id}/report")
    public Result<GeoWebsiteReportResponse> getWebsiteReport(@PathVariable("id") Long id) {
        return Result.success(websiteMonitorService.getLatestReport(id));
    }

    @GetMapping("/queries/page")
    public PageResult<GeoQuery> pageQueries(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "disease", required = false) String disease,
            @RequestParam(value = "enabled", required = false) Integer enabled) {
        assertPage(pageNum, pageSize);
        IPage<GeoQuery> result = geoQueryService.page(new Page<>(pageNum, pageSize), doctorId, departmentId, disease, enabled);
        return PageResult.of(result);
    }

    @PostMapping("/monitor/run")
    public Result<List<GeoMonitorRecord>> runMonitor(@RequestBody(required = false) GeoMonitorRunRequest request) {
        return Result.success(geoMonitorService.runMonitor(request));
    }

    @GetMapping("/monitor/records/page")
    public PageResult<GeoMonitorRecord> pageMonitorRecords(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "queryId", required = false) Long queryId,
            @RequestParam(value = "engine", required = false) String engine,
            @RequestParam(value = "mentioned", required = false) Integer mentioned,
            @RequestParam(value = "cited", required = false) Integer cited) {
        assertPage(pageNum, pageSize);
        IPage<GeoMonitorRecord> result = geoMonitorService.page(new Page<>(pageNum, pageSize), queryId, engine, mentioned, cited);
        return PageResult.of(result);
    }

    @GetMapping("/monitor/tasks/page")
    public PageResult<GeoMonitorTask> pageMonitorTasks(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "engine", required = false) String engine,
            @RequestParam(value = "triggerType", required = false) String triggerType) {
        assertPage(pageNum, pageSize);
        IPage<GeoMonitorTask> result = geoMonitorService.pageTasks(new Page<>(pageNum, pageSize), status, engine, triggerType);
        return PageResult.of(result);
    }

    @GetMapping("/analytics/platform-matrix")
    public Result<Map<String, Object>> platformMatrix(@RequestParam(value = "limit", defaultValue = "30") Integer limit) {
        return Result.success(geoMonitorService.platformMatrix(limit == null ? 30 : limit));
    }

    @GetMapping("/analytics/trends")
    public Result<Map<String, Object>> trends(
            @RequestParam(value = "dimension", defaultValue = "hospital") String dimension,
            @RequestParam(value = "days", defaultValue = "14") Integer days) {
        return Result.success(geoMonitorService.trend(dimension, days == null ? 14 : days));
    }

    @GetMapping("/analytics/sources")
    public Result<Map<String, Object>> sources(@RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return Result.success(geoMonitorService.sourceAnalysis(limit == null ? 50 : limit));
    }

    @PostMapping("/probes/run")
    public Result<List<GeoDialogueProbe>> runProbes(@RequestBody(required = false) GeoProbeRunRequest request) {
        return Result.success(dialogueProbeService.schedule(request));
    }

    @GetMapping("/probes/page")
    public PageResult<GeoDialogueProbe> pageProbes(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "engine", required = false) String engine,
            @RequestParam(value = "probeMode", required = false) String probeMode,
            @RequestParam(value = "gapType", required = false) String gapType) {
        assertPage(pageNum, pageSize);
        IPage<GeoDialogueProbe> result = dialogueProbeService.page(
                new Page<>(pageNum, pageSize), status, engine, probeMode, gapType
        );
        return PageResult.of(result);
    }

    @GetMapping("/sources/assets/page")
    public PageResult<GeoSourceAsset> pageSourceAssets(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "official", required = false) Integer official,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "domain", required = false) String domain) {
        assertPage(pageNum, pageSize);
        IPage<GeoSourceAsset> result = sourceAssetService.page(
                new Page<>(pageNum, pageSize), sourceType, official, entityType, domain
        );
        return PageResult.of(result);
    }

    @PostMapping("/sources/assets/rebuild")
    public Result<Integer> rebuildSourceAssets(
            @RequestParam(value = "limit", defaultValue = "1000") Integer limit) {
        return Result.success(sourceAssetService.rebuildFromMonitorRecords(limit == null ? 1000 : limit));
    }

    @GetMapping("/site-recommendations/page")
    public PageResult<GeoSiteRecommendation> pageSiteRecommendations(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "recommendationType", required = false) String recommendationType) {
        assertPage(pageNum, pageSize);
        IPage<GeoSiteRecommendation> result = siteRecommendationService.page(
                new Page<>(pageNum, pageSize), status, entityType, recommendationType
        );
        return PageResult.of(result);
    }

    @PutMapping("/site-recommendations/{id}/status")
    public Result<GeoSiteRecommendation> updateSiteRecommendationStatus(
            @PathVariable("id") Long id,
            @RequestBody GeoSiteRecommendationStatusRequest request) {
        return Result.success(siteRecommendationService.updateStatus(id, request));
    }

    @PostMapping("/feedback/generate")
    public Result<List<GeoFeedbackSuggestion>> generateFeedback(@RequestBody(required = false) GeoFeedbackGenerateRequest request) {
        return Result.success(geoFeedbackService.generateSuggestions(request));
    }

    @GetMapping("/feedback/page")
    public PageResult<GeoFeedbackSuggestion> pageFeedback(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "queryId", required = false) Long queryId,
            @RequestParam(value = "articleId", required = false) Long articleId,
            @RequestParam(value = "status", required = false) Integer status) {
        assertPage(pageNum, pageSize);
        IPage<GeoFeedbackSuggestion> result = geoFeedbackService.page(new Page<>(pageNum, pageSize), queryId, articleId, status);
        return PageResult.of(result);
    }

    @PostMapping("/compliance/auditArticle")
    public Result<ComplianceAuditResult> auditArticle(@RequestParam("articleId") Long articleId) {
        ArticleInfo article = articleManagementMapper.selectById(articleId);
        if (article == null) {
            throw new AppException("ARTICLE_NOT_FOUND", "文章不存在", HttpStatus.NOT_FOUND);
        }
        return Result.success(medicalComplianceService.audit(article.getTitle(), article.getContent()));
    }

    @PostMapping("/compliance/auditText")
    public Result<ComplianceAuditResult> auditText(@RequestBody MedicalComplianceAuditRequest request) {
        if (request == null) {
            throw new AppException("PARAM_INVALID", "审核内容不能为空");
        }
        return Result.success(medicalComplianceService.audit(request.getTitle(), request.getContent()));
    }

    private void assertPage(Long pageNum, Long pageSize) {
        if (pageNum == null || pageNum <= 0 || pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new AppException("PARAM_INVALID", "分页参数不合法");
        }
    }
}
