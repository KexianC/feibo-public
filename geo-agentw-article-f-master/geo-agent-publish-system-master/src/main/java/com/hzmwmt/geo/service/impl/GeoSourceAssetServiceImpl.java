package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.geo.mapper.GeoMonitorRecordMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.mapper.GeoSourceAssetMapper;
import com.hzmwmt.geo.mapper.GeoSourceObservationMapper;
import com.hzmwmt.geo.model.dto.GeoEvidence;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoMonitorRecord;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.model.entity.GeoSourceAsset;
import com.hzmwmt.geo.model.entity.GeoSourceObservation;
import com.hzmwmt.geo.service.GeoAuthorityScorer;
import com.hzmwmt.geo.service.GeoSourceAssetService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GeoSourceAssetServiceImpl implements GeoSourceAssetService {

    @Resource
    private GeoSourceAssetMapper sourceAssetMapper;

    @Resource
    private GeoSourceObservationMapper sourceObservationMapper;

    @Resource
    private GeoMonitorRecordMapper monitorRecordMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoAuthorityScorer authorityScorer;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void capture(GeoQuery query, GeoMonitorRecord record, GeoEvidence evidence) {
        if (record == null || record.getId() == null) {
            return;
        }
        captureEvidence(query, record.getEngine(), "monitor", record.getId(), record.getMonitoredTime(), evidence);
    }

    @Override
    public void captureProbe(GeoQuery query, GeoDialogueProbe probe, GeoEvidence evidence) {
        if (probe == null || probe.getId() == null) {
            return;
        }
        captureEvidence(query, probe.getEngine(), "probe", probe.getId(), probe.getFinishedTime(), evidence);
    }

    @Override
    public int rebuildFromMonitorRecords(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        List<GeoMonitorRecord> records = monitorRecordMapper.selectList(new LambdaQueryWrapper<GeoMonitorRecord>()
                .eq(GeoMonitorRecord::getStatus, 1)
                .orderByDesc(GeoMonitorRecord::getMonitoredTime)
                .last("LIMIT " + safeLimit));
        int processed = 0;
        for (GeoMonitorRecord record : records) {
            GeoQuery query = geoQueryMapper.selectById(record.getQueryId());
            GeoEvidence evidence = GeoEvidence.builder()
                    .citedUrls(parseStringList(record.getCitedUrls()))
                    .sourceDetails(parseSourceDetails(record.getRawPayload()))
                    .build();
            capture(query, record, evidence);
            processed++;
        }
        return processed;
    }

    @Override
    public IPage<GeoSourceAsset> page(Page<GeoSourceAsset> page,
                                      String sourceType,
                                      Integer official,
                                      String entityType,
                                      String domain) {
        LambdaQueryWrapper<GeoSourceAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(sourceType), GeoSourceAsset::getSourceType, sourceType);
        wrapper.eq(official != null, GeoSourceAsset::getOfficial, official);
        wrapper.eq(StringUtils.hasText(entityType), GeoSourceAsset::getEntityType, entityType);
        wrapper.like(StringUtils.hasText(domain), GeoSourceAsset::getDomain, domain);
        wrapper.orderByDesc(GeoSourceAsset::getAuthorityScore)
                .orderByDesc(GeoSourceAsset::getCitationCount)
                .orderByDesc(GeoSourceAsset::getLastSeenTime);
        return sourceAssetMapper.selectPage(page, wrapper);
    }

    private void captureEvidence(GeoQuery query,
                                 String engine,
                                 String originType,
                                 Long originId,
                                 LocalDateTime observedTime,
                                 GeoEvidence evidence) {
        if (evidence == null) {
            return;
        }
        Map<String, Map<String, Object>> detailsByUrl = new LinkedHashMap<>();
        if (evidence.getSourceDetails() != null) {
            for (Map<String, Object> detail : evidence.getSourceDetails()) {
                String url = text(detail.get("url"));
                if (StringUtils.hasText(url)) {
                    detailsByUrl.put(url, detail);
                }
            }
        }
        Set<String> urls = new LinkedHashSet<>(detailsByUrl.keySet());
        if (evidence.getCitedUrls() != null) {
            evidence.getCitedUrls().stream().filter(StringUtils::hasText).forEach(urls::add);
        }
        for (String url : urls) {
            Map<String, Object> detail = detailsByUrl.getOrDefault(url, Map.of());
            GeoSourceAsset asset = findOrCreateAsset(query, url, text(detail.get("title")), observedTime);
            if (asset == null || !insertObservation(asset, query, engine, originType, originId, observedTime)) {
                continue;
            }
            Set<String> engines = new LinkedHashSet<>(parseStringList(asset.getEngines()));
            if (StringUtils.hasText(engine)) {
                engines.add(engine);
            }
            sourceAssetMapper.update(null, new LambdaUpdateWrapper<GeoSourceAsset>()
                    .eq(GeoSourceAsset::getId, asset.getId())
                    .set(GeoSourceAsset::getEngines, toJson(engines))
                    .set(GeoSourceAsset::getLastSeenTime, observedTime == null ? LocalDateTime.now() : observedTime)
                    .setSql("citation_count = citation_count + 1"));
        }
    }

    private GeoSourceAsset findOrCreateAsset(GeoQuery query, String url, String title, LocalDateTime observedTime) {
        String normalizedUrl = url.trim();
        String urlHash = sha256(normalizedUrl);
        GeoSourceAsset existing = sourceAssetMapper.selectOne(new LambdaQueryWrapper<GeoSourceAsset>()
                .eq(GeoSourceAsset::getUrlHash, urlHash)
                .last("LIMIT 1"));
        if (existing != null) {
            if (!StringUtils.hasText(existing.getTitle()) && StringUtils.hasText(title)) {
                GeoSourceAsset update = new GeoSourceAsset();
                update.setId(existing.getId());
                update.setTitle(truncate(title, 500));
                sourceAssetMapper.updateById(update);
                existing.setTitle(update.getTitle());
            }
            return existing;
        }
        String host = host(normalizedUrl);
        GeoAuthorityScorer.Classification classification = authorityScorer.classify(host, normalizedUrl);
        EntityScope scope = entityScope(query);
        LocalDateTime seenAt = observedTime == null ? LocalDateTime.now() : observedTime;
        GeoSourceAsset asset = GeoSourceAsset.builder()
                .id(snowflakeIdGenerator.nextId())
                .urlHash(urlHash)
                .url(normalizedUrl)
                .title(truncate(title, 500))
                .domain(host)
                .sourceType(classification.sourceType())
                .entityType(scope.type())
                .entityId(scope.id())
                .entityName(scope.name())
                .official(classification.official() ? 1 : 0)
                .authorityScore(classification.authorityScore())
                .citationCount(0)
                .engines("[]")
                .verificationStatus(classification.official() ? 1 : 0)
                .usageRecommendation(classification.recommendation())
                .firstSeenTime(seenAt)
                .lastSeenTime(seenAt)
                .build();
        try {
            sourceAssetMapper.insert(asset);
            return asset;
        } catch (DuplicateKeyException ignored) {
            return sourceAssetMapper.selectOne(new LambdaQueryWrapper<GeoSourceAsset>()
                    .eq(GeoSourceAsset::getUrlHash, urlHash)
                    .last("LIMIT 1"));
        }
    }

    private boolean insertObservation(GeoSourceAsset asset,
                                      GeoQuery query,
                                      String engine,
                                      String originType,
                                      Long originId,
                                      LocalDateTime observedTime) {
        GeoSourceObservation observation = GeoSourceObservation.builder()
                .id(snowflakeIdGenerator.nextId())
                .sourceAssetId(asset.getId())
                .originType(originType)
                .originId(originId)
                .queryId(query == null ? null : query.getId())
                .engine(engine)
                .observedTime(observedTime == null ? LocalDateTime.now() : observedTime)
                .build();
        try {
            sourceObservationMapper.insert(observation);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    private EntityScope entityScope(GeoQuery query) {
        if (query == null) {
            return new EntityScope("topic", null, null);
        }
        if (query.getDoctorId() != null) {
            return new EntityScope("doctor", query.getDoctorId(), query.getDoctorName());
        }
        if (query.getDepartmentId() != null) {
            return new EntityScope("department", query.getDepartmentId(), query.getDepartmentName());
        }
        return new EntityScope("hospital", query.getHospitalId(), query.getHospitalName());
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> parseSourceDetails(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            if (json.trim().startsWith("[")) {
                return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
            }
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
            Object references = payload.get("references");
            if (references instanceof List<?> list) {
                List<Map<String, Object>> details = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> detail = new LinkedHashMap<>();
                        map.forEach((key, value) -> detail.put(String.valueOf(key), value));
                        details.add(detail);
                    }
                }
                return details;
            }
        } catch (Exception ignored) {
            // A cited URL list still allows a source asset to be created.
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash GEO source URL", e);
        }
    }

    private String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private record EntityScope(String type, Long id, String name) {
    }
}
