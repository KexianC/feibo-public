package com.hzmwmt.geo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.geo.mapper.GeoQueryMapper;
import com.hzmwmt.geo.model.dto.GeoQueryGenerateRequest;
import com.hzmwmt.geo.model.entity.GeoQuery;
import com.hzmwmt.geo.service.GeoQueryService;
import com.hzmwmt.mapper.DepartmentManagementMapper;
import com.hzmwmt.mapper.DoctorManagementMapper;
import com.hzmwmt.mapper.HospitalManagementMapper;
import com.hzmwmt.model.entity.DepartmentInfo;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.model.entity.HospitalInfo;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class GeoQueryServiceImpl implements GeoQueryService {

    @Resource
    private GeoQueryMapper geoQueryMapper;

    @Resource
    private DoctorManagementMapper doctorManagementMapper;

    @Resource
    private DepartmentManagementMapper departmentManagementMapper;

    @Resource
    private HospitalManagementMapper hospitalManagementMapper;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public List<GeoQuery> generateQueries(GeoQueryGenerateRequest request) {
        if (request == null) {
            throw new AppException("PARAM_INVALID", "GEO查询生成参数不能为空");
        }
        DoctorInfo doctor = request.getDoctorId() == null ? null : doctorManagementMapper.selectById(request.getDoctorId());
        DepartmentInfo department = request.getDepartmentId() == null ? null : departmentManagementMapper.selectById(request.getDepartmentId());
        HospitalInfo hospital = request.getHospitalId() == null ? null : hospitalManagementMapper.selectById(request.getHospitalId());

        if (doctor != null) {
            if (department == null && doctor.getDepartmentId() != null) {
                department = departmentManagementMapper.selectById(doctor.getDepartmentId());
            }
            if (hospital == null && doctor.getHospitalId() != null) {
                hospital = hospitalManagementMapper.selectById(doctor.getHospitalId());
            }
        }

        List<String> keywords = normalizeOptional(request.getKeywords());
        List<String> diseases = normalize(request.getDiseases(), "常见健康问题");
        List<String> scenarios = normalize(request.getTreatmentScenarios(), "就诊前咨询", "治疗后康复", "日常预防");
        String region = StringUtils.hasText(request.getRegion()) ? request.getRegion().trim() : inferRegion(hospital);

        List<GeoQuery> generated = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        if (!keywords.isEmpty()) {
            for (String keyword : keywords) {
                addKeywordQueries(generated, dedupe, request, doctor, department, hospital, region, keyword);
            }
        } else {
            for (String disease : diseases) {
                for (String scenario : scenarios) {
                    addKeywordQueries(generated, dedupe, request, doctor, department, hospital, region, disease, scenario);
                }
            }
        }

        if (Boolean.TRUE.equals(request.getPersist())) {
            for (GeoQuery query : generated) {
                if (!exists(query)) {
                    geoQueryMapper.insert(query);
                }
            }
        }
        return generated;
    }

    private void addKeywordQueries(List<GeoQuery> generated, Set<String> dedupe, GeoQueryGenerateRequest request,
                                   DoctorInfo doctor, DepartmentInfo department, HospitalInfo hospital,
                                   String region, String keyword) {
        addKeywordQueries(generated, dedupe, request, doctor, department, hospital, region, keyword, keyword);
    }

    private void addKeywordQueries(List<GeoQuery> generated, Set<String> dedupe, GeoQueryGenerateRequest request,
                                   DoctorInfo doctor, DepartmentInfo department, HospitalInfo hospital,
                                   String region, String disease, String scenario) {
        for (QueryTemplate template : userIntentTemplates(department, region, disease, scenario)) {
            addQuery(generated, dedupe, request, doctor, department, hospital, region, disease, scenario,
                    template.cluster(), template.queryText());
        }
    }

    @Override
    public GeoQuery addManualQuery(GeoQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryText())) {
            throw new AppException("PARAM_INVALID", "GEO查询内容不能为空");
        }
        query.setId(snowflakeIdGenerator.nextId());
        query.setSourceType(query.getSourceType() == null ? 2 : query.getSourceType());
        query.setPriority(query.getPriority() == null ? 50 : query.getPriority());
        query.setMonitorIntervalHours(normalizeInterval(query.getMonitorIntervalHours()));
        query.setEnabled(query.getEnabled() == null ? 1 : query.getEnabled());
        geoQueryMapper.insert(query);
        return query;
    }

    @Override
    public GeoQuery updateQuery(Long id, GeoQuery query) {
        if (id == null || query == null) {
            throw new AppException("PARAM_INVALID", "GEO查询更新参数不能为空");
        }
        GeoQuery existing = geoQueryMapper.selectById(id);
        if (existing == null) {
            throw new AppException("GEO_QUERY_NOT_FOUND", "GEO查询不存在");
        }
        GeoQuery update = new GeoQuery();
        update.setId(id);
        update.setQueryText(blankToNull(query.getQueryText()));
        update.setQueryCluster(blankToNull(query.getQueryCluster()));
        update.setDisease(blankToNull(query.getDisease()));
        update.setTreatmentScenario(blankToNull(query.getTreatmentScenario()));
        update.setRegion(blankToNull(query.getRegion()));
        update.setExpectedTerms(blankToNull(query.getExpectedTerms()));
        update.setDoctorId(query.getDoctorId());
        update.setDoctorName(blankToNull(query.getDoctorName()));
        update.setDepartmentId(query.getDepartmentId());
        update.setDepartmentName(blankToNull(query.getDepartmentName()));
        update.setHospitalId(query.getHospitalId());
        update.setHospitalName(blankToNull(query.getHospitalName()));
        update.setPriority(query.getPriority());
        update.setMonitorIntervalHours(query.getMonitorIntervalHours() == null ? null : normalizeInterval(query.getMonitorIntervalHours()));
        update.setEnabled(query.getEnabled() == null ? null : normalizeEnabled(query.getEnabled()));
        geoQueryMapper.updateById(update);
        return geoQueryMapper.selectById(id);
    }

    @Override
    public IPage<GeoQuery> page(Page<GeoQuery> page, Long doctorId, Long departmentId, String disease, Integer enabled) {
        LambdaQueryWrapper<GeoQuery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(doctorId != null, GeoQuery::getDoctorId, doctorId);
        wrapper.eq(departmentId != null, GeoQuery::getDepartmentId, departmentId);
        wrapper.like(StringUtils.hasText(disease), GeoQuery::getDisease, disease);
        wrapper.eq(enabled != null, GeoQuery::getEnabled, enabled);
        wrapper.orderByDesc(GeoQuery::getPriority).orderByDesc(GeoQuery::getCreatedTime);
        return geoQueryMapper.selectPage(page, wrapper);
    }

    @Override
    public List<GeoQuery> nextMonitorBatch(int limit, int staleHours) {
        LocalDateTime now = LocalDateTime.now();
        int fetchLimit = Math.max(50, Math.max(1, limit) * 10);
        return geoQueryMapper.selectList(new LambdaQueryWrapper<GeoQuery>()
                .eq(GeoQuery::getEnabled, 1)
                .orderByDesc(GeoQuery::getPriority)
                .orderByAsc(GeoQuery::getLastMonitorTime)
                .last("LIMIT " + fetchLimit))
                .stream()
                .filter(query -> isDue(query, staleHours, now))
                .limit(Math.max(1, limit))
                .toList();
    }

    private void addQuery(List<GeoQuery> target, Set<String> dedupe, GeoQueryGenerateRequest request,
                          DoctorInfo doctor, DepartmentInfo department, HospitalInfo hospital,
                          String region, String disease, String scenario, String cluster, String queryText) {
        String userQueryText = sanitizeGeneratedQuery(queryText, hospital, doctor);
        if (!dedupe.add(userQueryText)) {
            return;
        }
        GeoQuery query = GeoQuery.builder()
                .id(snowflakeIdGenerator.nextId())
                .doctorId(doctor == null ? request.getDoctorId() : doctor.getId())
                .doctorName(doctor == null ? null : doctor.getDoctorName())
                .departmentId(department == null ? request.getDepartmentId() : department.getId())
                .departmentName(department == null ? null : department.getDepartmentName())
                .hospitalId(hospital == null ? request.getHospitalId() : hospital.getId())
                .hospitalName(hospital == null ? null : hospital.getHospitalName())
                .region(region)
                .disease(disease)
                .treatmentScenario(scenario)
                .queryCluster(cluster)
                .queryText(userQueryText)
                .expectedTerms(expectedTermsJson(doctor, department, hospital))
                .sourceType(1)
                .priority(request.getPriority() == null ? 50 : request.getPriority())
                .monitorIntervalHours(normalizeInterval(request.getMonitorIntervalHours()))
                .enabled(1)
                .build();
        target.add(query);
    }

    private boolean isDue(GeoQuery query, int fallbackHours, LocalDateTime now) {
        if (query.getLastMonitorTime() == null) {
            return true;
        }
        int interval = query.getMonitorIntervalHours() == null ? Math.max(1, fallbackHours) : normalizeInterval(query.getMonitorIntervalHours());
        return !query.getLastMonitorTime().plusHours(interval).isAfter(now);
    }

    private Integer normalizeInterval(Integer value) {
        if (value == null) {
            return 24;
        }
        return Math.max(1, Math.min(720, value));
    }

    private Integer normalizeEnabled(Integer value) {
        return Objects.equals(value, 1) ? 1 : 0;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean exists(GeoQuery query) {
        Long count = geoQueryMapper.selectCount(new LambdaQueryWrapper<GeoQuery>()
                .eq(GeoQuery::getQueryText, query.getQueryText())
                .eq(query.getDoctorId() != null, GeoQuery::getDoctorId, query.getDoctorId())
                .eq(query.getDepartmentId() != null, GeoQuery::getDepartmentId, query.getDepartmentId()));
        return count != null && count > 0;
    }

    private List<String> normalize(List<String> source, String... defaults) {
        List<String> values = source == null ? List.of(defaults) : source;
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> normalizeOptional(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String expectedTermsJson(DoctorInfo doctor, DepartmentInfo department, HospitalInfo hospital) {
        List<String> terms = new ArrayList<>();
        if (doctor != null && StringUtils.hasText(doctor.getDoctorName())) {
            terms.add(doctor.getDoctorName());
        }
        if (department != null && StringUtils.hasText(department.getDepartmentName())) {
            terms.add(department.getDepartmentName());
        }
        if (hospital != null && StringUtils.hasText(hospital.getHospitalName())) {
            terms.add(hospital.getHospitalName());
        }
        try {
            return objectMapper.writeValueAsString(terms);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }


    private String inferRegion(HospitalInfo hospital) {
        if (hospital == null) {
            return "";
        }
        return List.of(hospital.getProvince(), hospital.getCity(), hospital.getDistrict()).stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String displayDepartment(DepartmentInfo department) {
        return department != null && StringUtils.hasText(department.getDepartmentName()) ? department.getDepartmentName() : "相关科室";
    }

    private List<QueryTemplate> userIntentTemplates(DepartmentInfo department, String region, String disease, String scenario) {
        if (isSkinBeautyIntent(department, disease, scenario)) {
            return skinBeautyTemplates(region);
        }
        return genericUserIntentTemplates(department, region, disease, scenario);
    }

    private List<QueryTemplate> skinBeautyTemplates(String region) {
        String city = cityName(region);
        String cityShi = city.endsWith("市") ? city : city + "市";
        String cityUrban = city.endsWith("市") ? city + "区" : city + "市区";
        return List.of(
                new QueryTemplate("地域+需求", "%s哪家医院做中医祛斑效果比较好？".formatted(city)),
                new QueryTemplate("地域+需求", "%s里治疗痘痘的中医医院有推荐吗？".formatted(cityShi)),
                new QueryTemplate("地域+需求", "%s哪里有正规的中医美容院或者医院科室？".formatted(city)),
                new QueryTemplate("地域+需求", "%s哪个医院可以用针灸减肥？".formatted(city)),
                new QueryTemplate("地域+需求", "%s哪里看脱发比较靠谱？最好是中医的。".formatted(cityUrban)),
                new QueryTemplate("地域+需求", "%s有没有能埋线减肥的公立医院？".formatted(city)),
                new QueryTemplate("地域+需求", "%s哪家医院的激光美容科比较好？".formatted(city)),
                new QueryTemplate("症状/疗法", "脸上的雀斑越来越多，中医有什么办法吗？"),
                new QueryTemplate("症状/疗法", "中医治疗黄褐斑一般用什么方法？有效吗？"),
                new QueryTemplate("症状/疗法", "反复长痘好几年了，中医能根治吗？一般怎么治？"),
                new QueryTemplate("症状/疗法", "中医针灸美容真的能除皱吗？效果怎么样？"),
                new QueryTemplate("症状/疗法", "脂溢性脱发看中医好还是西医好？%s哪边有专治的？".formatted(city)),
                new QueryTemplate("症状/疗法", "脸上痘印很多，中医有什么推荐的疗法或者面膜？"),
                new QueryTemplate("症状/疗法", "内分泌失调导致脸色暗沉，中医美容科能调理吗？"),
                new QueryTemplate("机构对比/选择", "治脸上的斑，去公立医院皮肤科还是去私立美容机构好？"),
                new QueryTemplate("机构对比/选择", "%s治疗痤疮，中西医结合哪家医院口碑好？".formatted(city)),
                new QueryTemplate("机构对比/选择", "%s公立医院里，哪家皮肤科中医特色最鲜明？".formatted(city)),
                new QueryTemplate("机构对比/选择", "想给脸上做激光祛斑，%s有哪些正规医院可以选择？".formatted(city)),
                new QueryTemplate("专家找医生", "%s有没有擅长治疗女性面部色斑的老中医或者专家？".formatted(city)),
                new QueryTemplate("专家找医生", "%s哪个医院的皮肤科有专门看脱发的专家门诊？".formatted(city))
        );
    }

    private List<QueryTemplate> genericUserIntentTemplates(DepartmentInfo department, String region, String disease, String scenario) {
        String city = cityName(region);
        String topic = StringUtils.hasText(disease) ? disease.trim() : "相关健康问题";
        String departmentName = displayDepartment(department);
        String scenarioText = StringUtils.hasText(scenario) ? scenario.trim() : "就诊";
        return List.of(
                new QueryTemplate("地域+需求", "%s哪家医院看%s比较好？".formatted(city, topic)),
                new QueryTemplate("地域+需求", "%s哪里有正规的%s门诊或者科室？".formatted(city, topic)),
                new QueryTemplate("症状/疗法", "%s一般怎么处理？需要看%s吗？".formatted(topic, departmentName)),
                new QueryTemplate("症状/疗法", "%s在%s阶段需要注意什么？".formatted(topic, scenarioText)),
                new QueryTemplate("机构对比/选择", "%s应该去公立医院还是专科机构？".formatted(topic)),
                new QueryTemplate("专家找医生", "%s有没有擅长%s的医生或者专家？".formatted(city, topic))
        );
    }

    private boolean isSkinBeautyIntent(DepartmentInfo department, String disease, String scenario) {
        String topic = String.join(" ", safe(disease), safe(scenario));
        String departmentName = department == null ? "" : safe(department.getDepartmentName());
        return topic.contains("美容")
                || topic.contains("祛斑")
                || topic.contains("黄褐斑")
                || topic.contains("痘")
                || topic.contains("脱发")
                || topic.contains("针灸")
                || topic.contains("减肥")
                || topic.contains("埋线")
                || topic.contains("激光")
                || (departmentName.contains("皮肤") && topic.contains("色斑"));
    }

    private String cityName(String region) {
        if (!StringUtils.hasText(region)) {
            return "本地";
        }
        String city = region.trim();
        if (city.endsWith("市")) {
            city = city.substring(0, city.length() - 1);
        }
        return StringUtils.hasText(city) ? city : "本地";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String sanitizeGeneratedQuery(String queryText, HospitalInfo hospital, DoctorInfo doctor) {
        String sanitized = StringUtils.hasText(queryText) ? queryText.trim() : "";
        if (hospital != null && StringUtils.hasText(hospital.getHospitalName())) {
            sanitized = sanitized.replace(hospital.getHospitalName().trim(), "");
        }
        if (doctor != null && StringUtils.hasText(doctor.getDoctorName())) {
            sanitized = sanitized.replace(doctor.getDoctorName().trim(), "");
        }
        sanitized = sanitized.replace("关于的", "关于");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(sanitized)) {
            return "相关健康问题有哪些权威科普资料可以参考？";
        }
        return sanitized;
    }

    private record QueryTemplate(String cluster, String queryText) {
    }
}
