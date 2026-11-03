package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.mapper.HospitalManagementMapper;
import com.hzmwmt.model.dto.HospitalStatisticsResponseDTO;
import com.hzmwmt.model.entity.HospitalInfo;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.service.HospitalManagementService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HospitalManagementServiceImpl implements HospitalManagementService {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private HospitalManagementMapper hospitalManagementMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public void addHospital(HospitalInfo hospitalInfo) {
        hospitalInfo.setId(snowflakeIdGenerator.nextId());
        if (hospitalInfo.getEnableStatus() == null) {
            hospitalInfo.setEnableStatus(1);
        }
        int rows = hospitalManagementMapper.insert(hospitalInfo);
        if (rows != 1) {
            throw new RuntimeException("新增医院失败，数据库影响行数异常");
        }
    }

    @Override
    public void editHospital(HospitalInfo hospitalInfo) {
        int rows = hospitalManagementMapper.updateById(hospitalInfo);
        if (rows != 1) {
            throw new RuntimeException("更新医院信息失败，该医院可能不存在");
        }
    }

    @Override
    public void deleteHospital(Long id) {
        int rows = hospitalManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new RuntimeException("删除失败");
        }
    }

    @Override
    public IPage<HospitalInfo> page(Page<HospitalInfo> page, String hospitalName, String hospitalLevel, String hospitalType, Integer statue) {
        LambdaQueryWrapper<HospitalInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(hospitalName != null && !hospitalName.isEmpty(), HospitalInfo::getHospitalName, hospitalName);
        queryWrapper.eq(statue != null, HospitalInfo::getEnableStatus, statue);
        queryWrapper.eq(hospitalLevel!=null&&!hospitalLevel.isEmpty(),HospitalInfo::getHospitalLevel,hospitalLevel);
        queryWrapper.eq(hospitalType!=null&&!hospitalType.isEmpty(),HospitalInfo::getHospitalType,hospitalType);

        IPage<HospitalInfo> result = hospitalManagementMapper.selectPage(page, queryWrapper);
        attachKnowledgeStats(result.getRecords());
        return result;
    }

    @Override
    public HospitalStatisticsResponseDTO statistics(String hospitalName, String hospitalLevel, String hospitalType, Integer statue) {
        HospitalStatisticsResponseDTO res = new HospitalStatisticsResponseDTO();

        LambdaQueryWrapper<HospitalInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(hospitalName != null && !hospitalName.isEmpty(), HospitalInfo::getHospitalName, hospitalName);
        queryWrapper.eq(statue != null, HospitalInfo::getEnableStatus, statue);
        queryWrapper.eq(hospitalLevel!=null&&!hospitalLevel.isEmpty(),HospitalInfo::getHospitalLevel,hospitalLevel);
        queryWrapper.eq(hospitalType!=null&&!hospitalType.isEmpty(),HospitalInfo::getHospitalType,hospitalType);

        res.setTotal(hospitalManagementMapper.selectCount(queryWrapper));
        res.setEnabled(hospitalManagementMapper.selectCount(queryWrapper.clone().eq(HospitalInfo::getEnableStatus, 1)));
        res.setDisabled(hospitalManagementMapper.selectCount(queryWrapper.clone().eq(HospitalInfo::getEnableStatus, 0)));
        // 2. 统计不同城市的数量（使用 QueryWrapper + COUNT(DISTINCT city)）
        QueryWrapper<HospitalInfo> cityWrapper = new QueryWrapper<>();
        // 复用原来的动态条件（注意：city 统计同样受 hospitalName 和 statue 过滤）
        if (hospitalName != null && !hospitalName.isEmpty()) {
            cityWrapper.like("hospital_name", hospitalName);  // 字段名根据实际列名调整
        }
        if (statue != null) {
            cityWrapper.eq("enable_status", statue);
        }
        if(hospitalLevel!=null&&!hospitalLevel.isEmpty()){
            cityWrapper.eq("hospital_level",hospitalLevel);
        }
        if(hospitalType!=null&&!hospitalType.isEmpty()){
            cityWrapper.eq("hospital_type",hospitalType);
        }
        cityWrapper.select("COUNT(DISTINCT city) AS cityCount");
        Map<String, Object> map = hospitalManagementMapper.selectMaps(cityWrapper).get(0);
        Long cityCount = (Long) map.get("cityCount");
        res.setCity(cityCount);
        return res;
    }

    @Override
    public void toggleStatue(Long id, Integer statue) {
        LambdaUpdateWrapper<HospitalInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(HospitalInfo::getId,id)
                .set(HospitalInfo::getEnableStatus, statue);
        hospitalManagementMapper.update(updateWrapper);
    }

    @Override
    public List<HospitalInfo> all() {
        LambdaQueryWrapper<HospitalInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(HospitalInfo::getEnableStatus,1);
        List<HospitalInfo> result = hospitalManagementMapper.selectList(qw);
        attachKnowledgeStats(result);
        return result;
    }

    private void attachKnowledgeStats(List<HospitalInfo> hospitals) {
        if (hospitals == null || hospitals.isEmpty()) {
            return;
        }
        List<Long> ids = hospitals.stream().map(HospitalInfo::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getAgentType, KnowledgeAgentTypeConstants.HOSPITAL)
                .in(KnowledgeDocument::getHospitalId, ids));
        Map<Long, List<KnowledgeDocument>> docsByHospital = docs.stream()
                .filter(doc -> doc.getHospitalId() != null)
                .collect(Collectors.groupingBy(KnowledgeDocument::getHospitalId));
        hospitals.forEach(hospital -> {
            List<KnowledgeDocument> scopedDocs = docsByHospital.getOrDefault(hospital.getId(), List.of());
            hospital.setFileCount((long) scopedDocs.size());
            hospital.setKnowledgeStatus(resolveKnowledgeStatus(scopedDocs));
        });
    }

    private Integer resolveKnowledgeStatus(List<KnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return 0;
        }
        boolean hasFailed = docs.stream().anyMatch(doc -> Integer.valueOf(2).equals(doc.getStatus()));
        if (hasFailed) {
            return 2;
        }
        boolean hasSuccess = docs.stream().anyMatch(doc -> Integer.valueOf(1).equals(doc.getStatus()));
        return hasSuccess ? 1 : 0;
    }
}
