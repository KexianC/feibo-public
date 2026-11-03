package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.ai.rag.mapper.KnowledgeDocumentMapper;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.mapper.DepartmentManagementMapper;
import com.hzmwmt.model.dto.DepartmentStatisticsResponseDTO;
import com.hzmwmt.model.entity.DepartmentInfo;
import com.hzmwmt.model.entity.KnowledgeDocument;
import com.hzmwmt.service.DepartmentManagementService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DepartmentManagementServiceImpl implements DepartmentManagementService {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private DepartmentManagementMapper departmentManagementMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public void addDepartment(DepartmentInfo departmentInfo) {
        departmentInfo.setId(snowflakeIdGenerator.nextId());
        if (departmentInfo.getEnableStatus() == null) {
            departmentInfo.setEnableStatus(1);
        }
        int rows = departmentManagementMapper.insert(departmentInfo);
        if (rows != 1) {
            throw new RuntimeException("新增科室失败，数据库影响行数异常");
        }
    }

    @Override
    public void editDepartment(DepartmentInfo departmentInfo) {
        int rows = departmentManagementMapper.updateById(departmentInfo);
        if (rows != 1) {
            throw new RuntimeException("更新科室信息失败，该科室可能不存在");
        }
    }

    @Override
    public void deleteDepartment(Long id) {
        int rows = departmentManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new RuntimeException("删除失败");
        }
    }

    @Override
    public IPage<DepartmentInfo> page(Page<DepartmentInfo> page, String departmentName, Long hospitalId, Integer statue) {
        LambdaQueryWrapper<DepartmentInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(departmentName != null && !departmentName.isEmpty(), DepartmentInfo::getDepartmentName, departmentName);
        queryWrapper.eq(statue != null, DepartmentInfo::getEnableStatus, statue);
        queryWrapper.eq(hospitalId != null, DepartmentInfo::getHospitalId, hospitalId);
        IPage<DepartmentInfo> result = departmentManagementMapper.selectPage(page, queryWrapper);
        attachKnowledgeStats(result.getRecords());
        return result;
    }

    @Override
    public void toggleStatue(Long id, Integer statue) {
        LambdaUpdateWrapper<DepartmentInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DepartmentInfo::getId,id)
                .set(DepartmentInfo::getEnableStatus, statue);
        departmentManagementMapper.update(updateWrapper);
    }

    @Override
    public List<DepartmentInfo> all(Long id) {
        LambdaQueryWrapper<DepartmentInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(DepartmentInfo::getEnableStatus,1)
                .eq(DepartmentInfo::getHospitalId,id);
        List<DepartmentInfo> result = departmentManagementMapper.selectList(qw);
        attachKnowledgeStats(result);
        return result;
    }

    @Override
    public DepartmentStatisticsResponseDTO statistics(String departmentName, Long hospitalId, Integer statue) {
        LambdaQueryWrapper<DepartmentInfo> qw = new LambdaQueryWrapper<>();
        qw.like(departmentName!=null&&!departmentName.isEmpty(),DepartmentInfo::getDepartmentName,departmentName)
                .eq(hospitalId!=null,DepartmentInfo::getHospitalId,hospitalId)
                .eq(statue!=null,DepartmentInfo::getEnableStatus,statue);

        DepartmentStatisticsResponseDTO res = new DepartmentStatisticsResponseDTO();
        res.setTotal(departmentManagementMapper.selectCount(qw));
        res.setEnabled(departmentManagementMapper.selectCount(qw.clone().eq(DepartmentInfo::getEnableStatus,1)));
        res.setDisabled(departmentManagementMapper.selectCount(qw.clone().eq(DepartmentInfo::getEnableStatus,0)));

        QueryWrapper<DepartmentInfo> queryWrapper = new QueryWrapper<>();
        if(departmentName!=null&&!departmentName.isEmpty()){
            queryWrapper.like("department_name",departmentName);
        }
        if(hospitalId!=null){
            queryWrapper.eq("hospital_id",hospitalId);
        }
        if(statue!=null){
            queryWrapper.eq("enable_status",statue);
        }
        queryWrapper.select("COUNT(DISTINCT hospital_id) AS hospitalCount");
        Map<String, Object> map = departmentManagementMapper.selectMaps(queryWrapper).get(0);
        res.setHospital((Long) map.get("hospitalCount"));

        return res;
    }

    private void attachKnowledgeStats(List<DepartmentInfo> departments) {
        if (departments == null || departments.isEmpty()) {
            return;
        }
        List<Long> ids = departments.stream().map(DepartmentInfo::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getAgentType, KnowledgeAgentTypeConstants.DEPARTMENT)
                .in(KnowledgeDocument::getDepartmentId, ids));
        Map<Long, List<KnowledgeDocument>> docsByDepartment = docs.stream()
                .filter(doc -> doc.getDepartmentId() != null)
                .collect(Collectors.groupingBy(KnowledgeDocument::getDepartmentId));
        departments.forEach(department -> {
            List<KnowledgeDocument> scopedDocs = docsByDepartment.getOrDefault(department.getId(), List.of());
            department.setFileCount((long) scopedDocs.size());
            department.setKnowledgeStatus(resolveKnowledgeStatus(scopedDocs));
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
