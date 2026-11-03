package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.mapper.DoctorManagementMapper;
import com.hzmwmt.model.dto.DoctorStatisticsResponseDTO;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.service.DoctorManagementService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DoctorManagementServiceImpl implements DoctorManagementService {
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private DoctorManagementMapper doctorManagementMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public void addDoctor(DoctorInfo doctorInfo) {
        Long id = snowflakeIdGenerator.nextId();  // 假设方法名为 nextId()
        doctorInfo.setId(id);

        if (doctorInfo.getGender() == null) {
            doctorInfo.setGender(0);   // 0-未知
        }
        if (doctorInfo.getKnowledgeStatus() == null) {
            doctorInfo.setKnowledgeStatus(0);  // 0-未上传
        }
        if (doctorInfo.getEnableStatus() == null) {
            doctorInfo.setEnableStatus(1);     // 1-启用
        }

        int rows = doctorManagementMapper.insert(doctorInfo);
        if (rows != 1) {
            throw new RuntimeException("新增医生失败，数据库影响行数异常");
        }
    }

    @Override
    public void editDoctor(DoctorInfo doctorInfo) {
        int rows = doctorManagementMapper.updateById(doctorInfo);
        if (rows != 1) {
            throw new RuntimeException("更新医生信息失败，该医生可能不存在");
        }
    }

    @Override
    public void deleteDoctor(Long id) {
        int rows = doctorManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new RuntimeException("删除失败");
        }
    }

    @Override
    public IPage<DoctorInfo> page(Page<DoctorInfo> page, String doctorName, Long hospital, Long department, Long title, Integer statue) {
        LambdaQueryWrapper<DoctorInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(doctorName != null && !doctorName.isEmpty(), DoctorInfo::getDoctorName, doctorName);
        queryWrapper.eq(hospital != null, DoctorInfo::getHospitalId, hospital);
        queryWrapper.eq(department != null, DoctorInfo::getDepartmentId, department);
        queryWrapper.eq(title != null, DoctorInfo::getTitleId, title);
        queryWrapper.eq(statue != null, DoctorInfo::getEnableStatus, statue);

        IPage<DoctorInfo> result = doctorManagementMapper.selectPage(page, queryWrapper);
        attachKnowledgeStats(result.getRecords());
        return result;

    }

    @Override
    public DoctorStatisticsResponseDTO stastics(String doctorName, Long hospital, Long department, Long title, Integer statue) {
        DoctorStatisticsResponseDTO res = new DoctorStatisticsResponseDTO();
        LambdaQueryWrapper<DoctorInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(doctorName != null && !doctorName.isEmpty(), DoctorInfo::getDoctorName, doctorName);
        queryWrapper.eq(hospital != null, DoctorInfo::getHospitalId, hospital);
        queryWrapper.eq(department != null, DoctorInfo::getDepartmentId, department);
        queryWrapper.eq(title != null, DoctorInfo::getTitleId, title);
        queryWrapper.eq(statue != null, DoctorInfo::getEnableStatus, statue);

        res.setTotal(doctorManagementMapper.selectCount(queryWrapper));
        res.setKnowledgeAvailable(doctorManagementMapper.selectCount(queryWrapper.clone().eq(DoctorInfo::getKnowledgeStatus, 1)));
        res.setEnabled(doctorManagementMapper.selectCount(queryWrapper.clone().eq(DoctorInfo::getEnableStatus, 1)));
        res.setDisabled(doctorManagementMapper.selectCount(queryWrapper.clone().eq(DoctorInfo::getEnableStatus, 0)));

        return res;
    }

    @Override
    public void toggleStatue(Long id, Integer statue) {
        LambdaUpdateWrapper<DoctorInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DoctorInfo::getId, id)
                .set(DoctorInfo::getEnableStatus, statue);
        doctorManagementMapper.update(updateWrapper);
    }

    @Override
    public List<DoctorInfo> all() {
        LambdaQueryWrapper<DoctorInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(DoctorInfo::getEnableStatus, 1);
        List<DoctorInfo> result = doctorManagementMapper.selectList(qw);
        attachKnowledgeStats(result);
        return result;
    }

    @Override
    public IPage<DoctorInfo> pageOther(Page<DoctorInfo> page, String doctorName, Long hospital, Long department, Long title, Integer knowledgeStatue) {
        LambdaQueryWrapper<DoctorInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(doctorName != null && !doctorName.isEmpty(), DoctorInfo::getDoctorName, doctorName);
        queryWrapper.eq(hospital != null, DoctorInfo::getHospitalId, hospital);
        queryWrapper.eq(department != null, DoctorInfo::getDepartmentId, department);
        queryWrapper.eq(title != null, DoctorInfo::getTitleId, title);
        queryWrapper.eq(DoctorInfo::getEnableStatus, 1);
        queryWrapper.eq(knowledgeStatue != null, DoctorInfo::getKnowledgeStatus, knowledgeStatue);
        IPage<DoctorInfo> result = doctorManagementMapper.selectPage(page, queryWrapper);
        attachKnowledgeStats(result.getRecords());
        return result;
    }

    private void attachKnowledgeStats(List<DoctorInfo> doctors) {
        if (doctors == null || doctors.isEmpty()) {
            return;
        }
        List<Long> ids = doctors.stream().map(DoctorInfo::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getAgentType, KnowledgeAgentTypeConstants.DOCTOR)
                .in(KnowledgeDocument::getDoctorId, ids));
        Map<Long, List<KnowledgeDocument>> docsByDoctor = docs.stream()
                .filter(doc -> doc.getDoctorId() != null)
                .collect(Collectors.groupingBy(KnowledgeDocument::getDoctorId));
        doctors.forEach(doctor -> {
            List<KnowledgeDocument> scopedDocs = docsByDoctor.getOrDefault(doctor.getId(), List.of());
            doctor.setFileCount((long) scopedDocs.size());
            doctor.setKnowledgeStatus(resolveKnowledgeStatus(scopedDocs));
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
