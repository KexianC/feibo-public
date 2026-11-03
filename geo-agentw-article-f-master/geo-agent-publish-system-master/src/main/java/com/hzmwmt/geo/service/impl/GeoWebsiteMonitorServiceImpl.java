package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.mapper.GeoSourceAssetMapper;
import com.hzmwmt.geo.mapper.GeoSourceObservationMapper;
import com.hzmwmt.geo.mapper.GeoWebsiteCheckMapper;
import com.hzmwmt.geo.mapper.GeoWebsiteMonitorMapper;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorCreateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorStatusResponse;
import com.hzmwmt.geo.model.dto.GeoWebsiteMonitorUpdateRequest;
import com.hzmwmt.geo.model.dto.GeoWebsiteReportResponse;
import com.hzmwmt.geo.model.entity.GeoSourceAsset;
import com.hzmwmt.geo.model.entity.GeoSourceObservation;
import com.hzmwmt.geo.model.entity.GeoWebsiteCheck;
import com.hzmwmt.geo.model.entity.GeoWebsiteMonitor;
import com.hzmwmt.geo.service.GeoOfficialDomainRegistry;
import com.hzmwmt.geo.service.GeoWebsiteMonitorService;
import com.hzmwmt.geo.service.GeoWebsiteMonitorWorker;
import com.hzmwmt.geo.service.GeoWebsiteUrlPolicy;
import com.hzmwmt.mapper.HospitalManagementMapper;
import com.hzmwmt.model.entity.HospitalInfo;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GeoWebsiteMonitorServiceImpl implements GeoWebsiteMonitorService {

    @Resource
    private GeoWebsiteMonitorMapper websiteMonitorMapper;

    @Resource
    private GeoWebsiteCheckMapper websiteCheckMapper;

    @Resource
    private HospitalManagementMapper hospitalManagementMapper;

    @Resource
    private GeoSourceAssetMapper sourceAssetMapper;

    @Resource
    private GeoSourceObservationMapper sourceObservationMapper;

    @Resource
    private GeoWebsiteUrlPolicy urlPolicy;

    @Resource
    private GeoWebsiteMonitorWorker worker;

    @Resource
    private GeoOfficialDomainRegistry officialDomainRegistry;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public GeoWebsiteMonitorStatusResponse create(GeoWebsiteMonitorCreateRequest request) {
        if (request == null || request.getHospitalId() == null) {
            throw new AppException("PARAM_INVALID", "请选择关联医院");
        }
        HospitalInfo hospital = hospitalManagementMapper.selectById(request.getHospitalId());
        if (hospital == null) {
            throw new AppException("HOSPITAL_NOT_FOUND", "关联医院不存在", HttpStatus.NOT_FOUND);
        }
        GeoWebsiteUrlPolicy.ValidatedUrl target = urlPolicy.validate(request.getSiteUrl());
        int interval = normalizeInterval(request.getMonitorIntervalHours());
        String urlHash = sha256(target.normalizedUrl());
        Long duplicateCount = websiteMonitorMapper.selectCount(new LambdaQueryWrapper<GeoWebsiteMonitor>()
                .eq(GeoWebsiteMonitor::getHospitalId, hospital.getId())
                .eq(GeoWebsiteMonitor::getUrlHash, urlHash));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new AppException("GEO_WEBSITE_EXISTS", "该医院已添加此官网监测");
        }
        String siteName = StringUtils.hasText(request.getSiteName())
                ? truncate(request.getSiteName().trim(), 255)
                : truncate(hospital.getHospitalName() + "官网", 255);
        boolean runImmediately = !Boolean.FALSE.equals(request.getRunImmediately());
        LocalDateTime now = LocalDateTime.now();
        LoginContextHolder.LoginUser operator = LoginContextHolder.currentUser();
        GeoWebsiteMonitor monitor = GeoWebsiteMonitor.builder()
                .id(snowflakeIdGenerator.nextId())
                .hospitalId(hospital.getId())
                .hospitalName(hospital.getHospitalName())
                .siteName(siteName)
                .siteUrl(request.getSiteUrl().trim())
                .normalizedUrl(target.normalizedUrl())
                .urlHash(urlHash)
                .domain(target.domain())
                .active(1)
                .monitorEnabled(1)
                .monitorIntervalHours(interval)
                .lastCheckStatus(0)
                .nextCheckTime(now.plusHours(interval))
                .createdBy(operator == null ? null : operator.userId())
                .build();
        try {
            websiteMonitorMapper.insert(monitor);
        } catch (DuplicateKeyException e) {
            throw new AppException("GEO_WEBSITE_EXISTS", "该医院已添加此官网监测");
        }
        officialDomainRegistry.invalidate();
        markExistingSourcesOfficial(monitor.getDomain());
        if (runImmediately) {
            enqueueCheck(monitor, "manual");
        }
        return toStatus(monitor);
    }

    @Override
    public IPage<GeoWebsiteMonitorStatusResponse> page(Page<GeoWebsiteMonitor> page,
                                                       Long hospitalId,
                                                       Integer active,
                                                       Integer monitorEnabled,
                                                       Integer lastCheckStatus,
                                                       String domain) {
        LambdaQueryWrapper<GeoWebsiteMonitor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(hospitalId != null, GeoWebsiteMonitor::getHospitalId, hospitalId);
        wrapper.eq(active != null, GeoWebsiteMonitor::getActive, active);
        wrapper.eq(monitorEnabled != null, GeoWebsiteMonitor::getMonitorEnabled, monitorEnabled);
        wrapper.eq(lastCheckStatus != null, GeoWebsiteMonitor::getLastCheckStatus, lastCheckStatus);
        wrapper.like(StringUtils.hasText(domain), GeoWebsiteMonitor::getDomain, domain == null ? null : domain.trim());
        wrapper.orderByDesc(GeoWebsiteMonitor::getActive)
                .orderByDesc(GeoWebsiteMonitor::getCreatedTime);
        IPage<GeoWebsiteMonitor> monitors = websiteMonitorMapper.selectPage(page, wrapper);
        Page<GeoWebsiteMonitorStatusResponse> result = new Page<>(monitors.getCurrent(), monitors.getSize(), monitors.getTotal());
        result.setRecords(monitors.getRecords().stream().map(this::toStatus).toList());
        return result;
    }

    @Override
    public GeoWebsiteMonitorStatusResponse update(Long id, GeoWebsiteMonitorUpdateRequest request) {
        GeoWebsiteMonitor existing = requireMonitor(id);
        if (request == null) {
            throw new AppException("PARAM_INVALID", "官网监测更新参数不能为空");
        }
        if (request.getActive() != null) {
            validateFlag(request.getActive(), "启用状态不合法");
        }
        if (request.getMonitorEnabled() != null) {
            validateFlag(request.getMonitorEnabled(), "定时监测状态不合法");
        }
        Integer interval = request.getMonitorIntervalHours() == null
                ? null
                : normalizeInterval(request.getMonitorIntervalHours());
        LambdaUpdateWrapper<GeoWebsiteMonitor> update = new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                .eq(GeoWebsiteMonitor::getId, id);
        boolean changed = false;
        if (StringUtils.hasText(request.getSiteName())) {
            update.set(GeoWebsiteMonitor::getSiteName, truncate(request.getSiteName().trim(), 255));
            changed = true;
        }
        if (request.getActive() != null) {
            update.set(GeoWebsiteMonitor::getActive, request.getActive());
            changed = true;
        }
        if (request.getMonitorEnabled() != null) {
            update.set(GeoWebsiteMonitor::getMonitorEnabled, request.getMonitorEnabled());
            changed = true;
        }
        if (interval != null) {
            update.set(GeoWebsiteMonitor::getMonitorIntervalHours, interval);
            changed = true;
        }
        boolean scheduleEnabled = (request.getActive() == null ? existing.getActive() : request.getActive()) == 1
                && (request.getMonitorEnabled() == null ? existing.getMonitorEnabled() : request.getMonitorEnabled()) == 1;
        if (changed && scheduleEnabled && (interval != null || Integer.valueOf(1).equals(request.getMonitorEnabled()))) {
            int effectiveInterval = interval == null ? normalizeInterval(existing.getMonitorIntervalHours()) : interval;
            update.set(GeoWebsiteMonitor::getNextCheckTime, LocalDateTime.now().plusHours(effectiveInterval));
        }
        if (changed) {
            websiteMonitorMapper.update(null, update);
        }
        if (request.getActive() != null) {
            officialDomainRegistry.invalidate();
        }
        return toStatus(requireMonitor(id));
    }

    @Override
    public GeoWebsiteCheck runCheck(Long id) {
        GeoWebsiteMonitor monitor = requireMonitor(id);
        if (!Integer.valueOf(1).equals(monitor.getActive())) {
            throw new AppException("GEO_WEBSITE_INACTIVE", "官网监测已停用，无法检测");
        }
        GeoWebsiteCheck existing = websiteCheckMapper.selectOne(new LambdaQueryWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getMonitorId, id)
                .in(GeoWebsiteCheck::getStatus, List.of(0, 1))
                .orderByDesc(GeoWebsiteCheck::getCreatedTime)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        return enqueueCheck(monitor, "manual");
    }

    @Override
    public IPage<GeoWebsiteCheck> checkPage(Page<GeoWebsiteCheck> page, Long monitorId, Integer status) {
        if (monitorId == null) {
            throw new AppException("PARAM_INVALID", "官网监测ID不能为空");
        }
        requireMonitor(monitorId);
        return websiteCheckMapper.selectPage(page, new LambdaQueryWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getMonitorId, monitorId)
                .eq(status != null, GeoWebsiteCheck::getStatus, status)
                .orderByDesc(GeoWebsiteCheck::getCreatedTime));
    }

    @Override
    public GeoWebsiteReportResponse getLatestReport(Long id) {
        GeoWebsiteMonitor monitor = requireMonitor(id);
        GeoWebsiteCheck latest = latestCheck(id);
        return GeoWebsiteReportResponse.builder()
                .monitor(toStatus(monitor))
                .latestCheck(latest)
                .checks(parseChecks(latest == null ? null : latest.getChecksJson()))
                .recommendations(parseRecommendations(latest == null ? null : latest.getRecommendationsJson()))
                .build();
    }

    private GeoWebsiteCheck enqueueCheck(GeoWebsiteMonitor monitor, String triggerType) {
        GeoWebsiteCheck check = worker.newPendingCheck(monitor, triggerType);
        websiteCheckMapper.insert(check);
        websiteMonitorMapper.update(null, new LambdaUpdateWrapper<GeoWebsiteMonitor>()
                .eq(GeoWebsiteMonitor::getId, monitor.getId())
                .set(GeoWebsiteMonitor::getLastCheckStatus, 0)
                .set(GeoWebsiteMonitor::getLastError, null));
        worker.processAsync(check);
        return check;
    }

    private GeoWebsiteMonitor requireMonitor(Long id) {
        if (id == null) {
            throw new AppException("PARAM_INVALID", "官网监测ID不能为空");
        }
        GeoWebsiteMonitor monitor = websiteMonitorMapper.selectById(id);
        if (monitor == null) {
            throw new AppException("GEO_WEBSITE_NOT_FOUND", "官网监测不存在", HttpStatus.NOT_FOUND);
        }
        return monitor;
    }

    private GeoWebsiteMonitorStatusResponse toStatus(GeoWebsiteMonitor monitor) {
        GeoWebsiteCheck latest = latestCheck(monitor.getId());
        CitationSummary citation = citationSummary(monitor.getDomain());
        return GeoWebsiteMonitorStatusResponse.builder()
                .id(monitor.getId())
                .hospitalId(monitor.getHospitalId())
                .hospitalName(monitor.getHospitalName())
                .siteName(monitor.getSiteName())
                .siteUrl(monitor.getSiteUrl())
                .normalizedUrl(monitor.getNormalizedUrl())
                .domain(monitor.getDomain())
                .active(monitor.getActive())
                .monitorEnabled(monitor.getMonitorEnabled())
                .monitorIntervalHours(monitor.getMonitorIntervalHours())
                .lastCheckStatus(monitor.getLastCheckStatus())
                .lastScore(monitor.getLastScore())
                .lastHttpStatus(monitor.getLastHttpStatus())
                .lastFinalUrl(monitor.getLastFinalUrl())
                .lastError(monitor.getLastError())
                .lastCheckedTime(monitor.getLastCheckedTime())
                .nextCheckTime(monitor.getNextCheckTime())
                .createdBy(monitor.getCreatedBy())
                .createdTime(monitor.getCreatedTime())
                .updatedTime(monitor.getUpdatedTime())
                .latestCheckId(latest == null ? null : latest.getId())
                .citationCount(citation.citationCount())
                .citingQueryCount(citation.queryCount())
                .citingEngines(citation.engines())
                .lastCitationTime(citation.lastCitationTime())
                .build();
    }

    private GeoWebsiteCheck latestCheck(Long monitorId) {
        return websiteCheckMapper.selectOne(new LambdaQueryWrapper<GeoWebsiteCheck>()
                .eq(GeoWebsiteCheck::getMonitorId, monitorId)
                .orderByDesc(GeoWebsiteCheck::getCreatedTime)
                .last("LIMIT 1"));
    }

    private CitationSummary citationSummary(String domain) {
        if (!StringUtils.hasText(domain)) {
            return CitationSummary.empty();
        }
        List<GeoSourceAsset> assets = sourceAssetMapper.selectList(new LambdaQueryWrapper<GeoSourceAsset>()
                        .like(GeoSourceAsset::getDomain, domain))
                .stream()
                .filter(asset -> officialDomainRegistry.matchesHost(asset.getDomain(), domain))
                .toList();
        if (assets.isEmpty()) {
            return CitationSummary.empty();
        }
        long citationCount = assets.stream().map(GeoSourceAsset::getCitationCount)
                .filter(value -> value != null).mapToLong(Integer::longValue).sum();
        Set<String> engines = new LinkedHashSet<>();
        assets.forEach(asset -> engines.addAll(parseEngines(asset.getEngines())));
        List<Long> assetIds = assets.stream().map(GeoSourceAsset::getId).toList();
        List<GeoSourceObservation> observations = sourceObservationMapper.selectList(
                new LambdaQueryWrapper<GeoSourceObservation>()
                        .in(GeoSourceObservation::getSourceAssetId, assetIds));
        long queryCount = observations.stream().map(GeoSourceObservation::getQueryId)
                .filter(value -> value != null).distinct().count();
        observations.stream().map(GeoSourceObservation::getEngine)
                .filter(StringUtils::hasText).forEach(engines::add);
        LocalDateTime lastCitation = observations.stream().map(GeoSourceObservation::getObservedTime)
                .filter(value -> value != null).max(Comparator.naturalOrder()).orElse(null);
        return new CitationSummary(citationCount, queryCount, new ArrayList<>(engines), lastCitation);
    }

    private void markExistingSourcesOfficial(String domain) {
        sourceAssetMapper.selectList(new LambdaQueryWrapper<GeoSourceAsset>().like(GeoSourceAsset::getDomain, domain))
                .stream()
                .filter(asset -> officialDomainRegistry.matchesHost(asset.getDomain(), domain))
                .forEach(asset -> sourceAssetMapper.update(null, new LambdaUpdateWrapper<GeoSourceAsset>()
                        .eq(GeoSourceAsset::getId, asset.getId())
                        .set(GeoSourceAsset::getSourceType, "official_site")
                        .set(GeoSourceAsset::getOfficial, 1)
                        .set(GeoSourceAsset::getAuthorityScore, 90)
                        .set(GeoSourceAsset::getVerificationStatus, 1)
                        .set(GeoSourceAsset::getUsageRecommendation, "作为医院、科室和医生事实的首要引用源")));
    }

    private List<String> parseEngines(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<java.util.Map<String, Object>> parseChecks(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<java.util.Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseRecommendations(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private int normalizeInterval(Integer value) {
        if (value == null || value < 1 || value > 720) {
            if (value == null) {
                return 24;
            }
            throw new AppException("PARAM_INVALID", "监测频率必须在1到720小时之间");
        }
        return value;
    }

    private void validateFlag(Integer value, String message) {
        if (!(Integer.valueOf(0).equals(value) || Integer.valueOf(1).equals(value))) {
            throw new AppException("PARAM_INVALID", message);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AppException("GEO_WEBSITE_HASH_ERROR", "官网地址处理失败");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record CitationSummary(long citationCount,
                                   long queryCount,
                                   List<String> engines,
                                   LocalDateTime lastCitationTime) {
        static CitationSummary empty() {
            return new CitationSummary(0, 0, List.of(), null);
        }
    }
}
