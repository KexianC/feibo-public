package com.hzmwmt.geo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoDialogueProbeMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.dto.GeoGapAnalysis;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.platform.GeoPlatformAdapter;
import com.hzmwmt.geo.service.platform.GeoPlatformRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class GeoDialogueProbeWorker {

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private GeoDialogueProbeMapper dialogueProbeMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoEngineCatalog geoEngineCatalog;

    @Resource
    private GeoPlatformRegistry geoPlatformRegistry;

    @Resource
    private GeoPlatformCallService geoPlatformCallService;

    @Resource
    private GeoEvidenceParser geoEvidenceParser;

    @Resource
    private GeoVisibilityScorer geoVisibilityScorer;

    @Resource
    private GeoGapClassifier geoGapClassifier;

    @Resource
    private GeoProbeAnalysisAssistant probeAnalysisAssistant;

    @Resource
    private GeoSourceAssetService geoSourceAssetService;

    @Resource
    private GeoSiteRecommendationService siteRecommendationService;

    @Resource
    private ObjectMapper objectMapper;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(Math.max(1, geoProperties.getProbeWorkerConcurrency()));
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${app.geo.probe-poll-ms:10000}")
    public void pollPendingProbes() {
        if (!geoProperties.isProbeEnabled()) {
            return;
        }
        List<GeoDialogueProbe> probes = dialogueProbeMapper.selectList(new LambdaQueryWrapper<GeoDialogueProbe>()
                .eq(GeoDialogueProbe::getStatus, 0)
                .orderByAsc(GeoDialogueProbe::getCreatedTime)
                .last("LIMIT " + Math.max(1, geoProperties.getProbeWorkerBatchSize())));
        for (GeoDialogueProbe probe : probes) {
            CompletableFuture.runAsync(() -> claimAndRun(probe), executorService)
                    .exceptionally(error -> {
                        log.warn("GEO dialogue probe future failed: probeId={}, exceptionType={}",
                                probe.getId(), error.getClass().getSimpleName());
                        return null;
                    });
        }
    }

    private void claimAndRun(GeoDialogueProbe probe) {
        GeoDialogueProbe claim = new GeoDialogueProbe();
        claim.setId(probe.getId());
        claim.setStatus(1);
        claim.setStartedTime(LocalDateTime.now());
        int updated = dialogueProbeMapper.update(claim, new LambdaQueryWrapper<GeoDialogueProbe>()
                .eq(GeoDialogueProbe::getId, probe.getId())
                .eq(GeoDialogueProbe::getStatus, 0));
        if (updated < 1) {
            return;
        }
        runProbe(dialogueProbeMapper.selectById(probe.getId()));
    }

    private void runProbe(GeoDialogueProbe probe) {
        GeoQuery query = geoQueryMapper.selectById(probe.getQueryId());
        try {
            if (query == null) {
                throw new IllegalStateException("GEO query not found: " + probe.getQueryId());
            }
            GeoProperties.EngineProperties engine = geoEngineCatalog.enabledEngine(probe.getEngine());
            GeoPlatformAdapter adapter = geoPlatformRegistry.resolve(engine);
            long startedAt = System.currentTimeMillis();
            GeoEngineAnswer baseline = geoPlatformCallService.ask(engine, adapter, probe.getQueryText());
            GeoEvidence evidence = geoEvidenceParser.parse(query, baseline, System.currentTimeMillis() - startedAt);
            List<Map<String, Object>> dialogue = new ArrayList<>();
            String finalDiagnosis = "";
            int completedRounds = 0;
            int rounds = Math.max(1, Math.min(probe.getRequestedRounds() == null ? 1 : probe.getRequestedRounds(),
                    Math.max(1, geoProperties.getProbeMaxRounds())));
            for (int round = 1; round <= rounds; round++) {
                String prompt = diagnosticPrompt(query, probe, evidence, finalDiagnosis, round);
                String answer = diagnose(engine, adapter, probe, prompt);
                Map<String, Object> turn = new LinkedHashMap<>();
                turn.put("round", round);
                turn.put("mode", probe.getProbeMode());
                turn.put("prompt", prompt);
                turn.put("answer", answer);
                dialogue.add(turn);
                if (StringUtils.hasText(answer)) {
                    finalDiagnosis = answer;
                    completedRounds++;
                }
            }
            GeoGapAnalysis analysis = geoGapClassifier.classify(query, evidence, finalDiagnosis);
            LocalDateTime finishedAt = LocalDateTime.now();
            GeoDialogueProbe success = new GeoDialogueProbe();
            success.setId(probe.getId());
            success.setStatus(2);
            success.setCompletedRounds(completedRounds);
            success.setMentioned(evidence.getMatchedTerms().isEmpty() ? 0 : 1);
            success.setCited(evidence.getCitedUrls().isEmpty() ? 0 : 1);
            success.setVisibilityScore(geoVisibilityScorer.score(evidence));
            success.setOfficialSourceCount(evidence.getOfficialSourceCount());
            success.setBaselineAnswer(evidence.getAnswerText());
            success.setBaselineSources(toJson(Map.of(
                    "citedUrls", evidence.getCitedUrls(),
                    "sourceDetails", evidence.getSourceDetails(),
                    "competitorMentions", evidence.getCompetitorMentions()
            )));
            success.setDialogueJson(toJson(dialogue));
            success.setGapType(analysis.getGapType());
            success.setGapSummary(analysis.getGapSummary());
            success.setSourceRequirements(toJson(analysis.getSourceRequirements()));
            success.setRecommendedActions(toJson(analysis.getRecommendedActions()));
            success.setFinishedTime(finishedAt);
            dialogueProbeMapper.updateById(success);

            GeoDialogueProbe completed = dialogueProbeMapper.selectById(probe.getId());
            geoSourceAssetService.captureProbe(query, completed, evidence);
            siteRecommendationService.createFromProbe(query, completed, analysis);
        } catch (Exception e) {
            log.warn("GEO dialogue probe failed: probeId={}, queryId={}, exceptionType={}",
                    probe.getId(), probe.getQueryId(), e.getClass().getSimpleName());
            GeoDialogueProbe failure = new GeoDialogueProbe();
            failure.setId(probe.getId());
            failure.setStatus(3);
            failure.setErrorMessage(truncate(e.getMessage(), 1000));
            failure.setFinishedTime(LocalDateTime.now());
            dialogueProbeMapper.updateById(failure);
        }
    }

    private String diagnose(GeoProperties.EngineProperties engine,
                            GeoPlatformAdapter adapter,
                            GeoDialogueProbe probe,
                            String prompt) {
        try {
            if ("evidence_analysis".equals(probe.getProbeMode())) {
                String response = probeAnalysisAssistant.analyze(prompt)
                        .collectList()
                        .map(parts -> String.join("", parts))
                        .block();
                return response == null ? "" : response;
            }
            GeoEngineAnswer answer = geoPlatformCallService.diagnose(engine, adapter, prompt);
            return answer == null ? "" : answer.getAnswerText();
        } catch (Exception e) {
            log.info("GEO probe diagnostic round fell back to rule analysis: probeId={}, exceptionType={}",
                    probe.getId(), e.getClass().getSimpleName());
            return "";
        }
    }

    private String diagnosticPrompt(GeoQuery query,
                                    GeoDialogueProbe probe,
                                    GeoEvidence evidence,
                                    String previousDiagnosis,
                                    int round) {
        String reviewInstruction = round == 1
                ? "请诊断未提及、未引用或未引用官方来源的原因。"
                : "请复核上一轮诊断，删除不可验证、刷量、伪装用户或把对话当作训练模型的建议。";
        return """
                这是内部 GEO 可观测性诊断，不是公开用户提问，也不会训练或改变公共模型。
                医院官网不在本系统控制范围内，所有官网动作只能写成提交给医院方的修改建议。
                %s
                输出严格 JSON：
                {"gapType":"authority_and_relevance_gap|official_source_gap|competitive_authority_gap|content_structure_gap",
                 "gapSummary":"",
                 "sourceRequirements":[""],
                 "recommendedActions":[""]}

                真实搜索问题：%s
                内部评估目标：医院=%s；科室=%s；医生=%s
                地区/关键词/场景：%s / %s / %s
                平台：%s
                是否提及：%s
                是否引用：%s
                官方来源数：%d
                竞品提及：%s
                基准回答：%s
                来源证据：%s
                上一轮诊断：%s
                """.formatted(
                reviewInstruction,
                query.getQueryText(),
                value(query.getHospitalName()),
                value(query.getDepartmentName()),
                value(query.getDoctorName()),
                value(query.getRegion()),
                value(query.getDisease()),
                value(query.getTreatmentScenario()),
                probe.getEngine(),
                evidence.getMatchedTerms().isEmpty() ? "否" : "是",
                evidence.getCitedUrls().isEmpty() ? "否" : "是",
                evidence.getOfficialSourceCount(),
                truncate(toJson(evidence.getCompetitorMentions()), 1000),
                truncate(evidence.getAnswerText(), geoProperties.getFeedbackContextLimit()),
                truncate(toJson(evidence.getSourceDetails()), geoProperties.getFeedbackContextLimit()),
                truncate(previousDiagnosis, 2000)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int limit = Math.max(100, maxLength);
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}
