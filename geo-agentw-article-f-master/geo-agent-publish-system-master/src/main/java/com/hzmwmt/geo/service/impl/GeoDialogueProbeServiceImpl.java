package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.mapper.GeoDialogueProbeMapper;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoProbeRunRequest;
import com.hzmwmt.geo.model.entity.GeoDialogueProbe;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.GeoDialogueProbeService;
import com.hzmwmt.geo.service.GeoEngineCatalog;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeoDialogueProbeServiceImpl implements GeoDialogueProbeService {

    @Resource
    private GeoDialogueProbeMapper dialogueProbeMapper;

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private GeoEngineCatalog geoEngineCatalog;

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public List<GeoDialogueProbe> schedule(GeoProbeRunRequest request) {
        List<GeoQuery> queries = resolveQueries(request);
        List<GeoProperties.EngineProperties> engines = geoEngineCatalog.enabledEngines(
                request == null ? null : request.getEngines()
        );
        if (queries.isEmpty() || engines.isEmpty()) {
            return List.of();
        }
        int requestedRounds = Math.max(1, Math.min(
                request == null || request.getRounds() == null ? 1 : request.getRounds(),
                Math.max(1, geoProperties.getProbeMaxRounds())
        ));
        List<GeoDialogueProbe> probes = new ArrayList<>();
        for (GeoQuery query : queries) {
            for (GeoProperties.EngineProperties engine : engines) {
                if (hasOpenProbe(query.getId(), engine.getName())) {
                    continue;
                }
                boolean searchOnly = "qianfan-ai-search".equalsIgnoreCase(engine.getProtocol());
                GeoDialogueProbe probe = GeoDialogueProbe.builder()
                        .id(snowflakeIdGenerator.nextId())
                        .queryId(query.getId())
                        .engine(engine.getName())
                        .queryText(query.getQueryText())
                        .probeMode(searchOnly ? "evidence_analysis" : "platform_self_review")
                        .requestedRounds(requestedRounds)
                        .completedRounds(0)
                        .status(0)
                        .mentioned(0)
                        .cited(0)
                        .visibilityScore(0)
                        .officialSourceCount(0)
                        .build();
                dialogueProbeMapper.insert(probe);
                probes.add(probe);
            }
        }
        return probes;
    }

    @Override
    public IPage<GeoDialogueProbe> page(Page<GeoDialogueProbe> page,
                                        Integer status,
                                        String engine,
                                        String probeMode,
                                        String gapType) {
        LambdaQueryWrapper<GeoDialogueProbe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, GeoDialogueProbe::getStatus, status);
        wrapper.eq(StringUtils.hasText(engine), GeoDialogueProbe::getEngine, engine);
        wrapper.eq(StringUtils.hasText(probeMode), GeoDialogueProbe::getProbeMode, probeMode);
        wrapper.eq(StringUtils.hasText(gapType), GeoDialogueProbe::getGapType, gapType);
        wrapper.orderByDesc(GeoDialogueProbe::getCreatedTime);
        return dialogueProbeMapper.selectPage(page, wrapper);
    }

    private List<GeoQuery> resolveQueries(GeoProbeRunRequest request) {
        if (request != null && !CollectionUtils.isEmpty(request.getQueryIds())) {
            return geoQueryMapper.selectBatchIds(request.getQueryIds());
        }
        int limit = Math.max(1, Math.min(
                request == null || request.getLimit() == null ? 10 : request.getLimit(),
                50
        ));
        return geoQueryMapper.selectList(new LambdaQueryWrapper<GeoQuery>()
                .eq(GeoQuery::getEnabled, 1)
                .orderByDesc(GeoQuery::getPriority)
                .orderByAsc(GeoQuery::getLastMonitorTime)
                .last("LIMIT " + limit));
    }

    private boolean hasOpenProbe(Long queryId, String engine) {
        Long count = dialogueProbeMapper.selectCount(new LambdaQueryWrapper<GeoDialogueProbe>()
                .eq(GeoDialogueProbe::getQueryId, queryId)
                .eq(GeoDialogueProbe::getEngine, engine)
                .in(GeoDialogueProbe::getStatus, 0, 1));
        return count != null && count > 0;
    }
}
