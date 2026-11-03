package com.hzmwmt.ai.rag.service;

import com.hzmwmt.ai.rag.model.dto.KnowledgeAgentScope;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.constant.KnowledgeAgentTypeConstants;
import com.hzmwmt.mapper.DepartmentManagementMapper;
import com.hzmwmt.mapper.DoctorManagementMapper;
import com.hzmwmt.mapper.HospitalManagementMapper;
import com.hzmwmt.model.entity.DepartmentInfo;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.model.entity.HospitalInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeAgentScopeResolver {

    @Resource
    private HospitalManagementMapper hospitalManagementMapper;

    @Resource
    private DepartmentManagementMapper departmentManagementMapper;

    @Resource
    private DoctorManagementMapper doctorManagementMapper;

    public KnowledgeAgentScope resolve(Integer agentType, Long hospitalId, Long departmentId, Long doctorId) {
        Integer normalizedType = normalizeAgentType(agentType, hospitalId, departmentId, doctorId);
        return switch (normalizedType) {
            case KnowledgeAgentTypeConstants.HOSPITAL -> resolveHospital(hospitalId);
            case KnowledgeAgentTypeConstants.DEPARTMENT -> resolveDepartment(departmentId, hospitalId);
            case KnowledgeAgentTypeConstants.DOCTOR -> resolveDoctor(doctorId);
            default -> throw new AppException("AGENT_SCOPE_INVALID", "智能体类型不合法");
        };
    }

    public KnowledgeAgentScope fromDocumentFields(Integer agentType, Long hospitalId, String hospitalName,
                                                  Long departmentId, String departmentName,
                                                  Long doctorId, String doctorName) {
        return KnowledgeAgentScope.builder()
                .agentType(agentType)
                .hospitalId(hospitalId)
                .hospitalName(hospitalName)
                .departmentId(departmentId)
                .departmentName(departmentName)
                .doctorId(doctorId)
                .doctorName(doctorName)
                .build();
    }

    private Integer normalizeAgentType(Integer agentType, Long hospitalId, Long departmentId, Long doctorId) {
        if (KnowledgeAgentTypeConstants.isValid(agentType)) {
            return agentType;
        }
        if (doctorId != null) {
            return KnowledgeAgentTypeConstants.DOCTOR;
        }
        if (departmentId != null) {
            return KnowledgeAgentTypeConstants.DEPARTMENT;
        }
        if (hospitalId != null) {
            return KnowledgeAgentTypeConstants.HOSPITAL;
        }
        throw new AppException("AGENT_SCOPE_INVALID", "必须指定医院、科室或医生智能体");
    }

    private KnowledgeAgentScope resolveHospital(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            throw new AppException("AGENT_SCOPE_INVALID", "医院智能体必须指定医院ID");
        }
        HospitalInfo hospital = hospitalManagementMapper.selectById(hospitalId);
        if (hospital == null) {
            throw new AppException("HOSPITAL_NOT_FOUND", "医院不存在");
        }
        return KnowledgeAgentScope.builder()
                .agentType(KnowledgeAgentTypeConstants.HOSPITAL)
                .hospitalId(hospital.getId())
                .hospitalName(hospital.getHospitalName())
                .build();
    }

    private KnowledgeAgentScope resolveDepartment(Long departmentId, Long fallbackHospitalId) {
        if (departmentId == null || departmentId <= 0) {
            throw new AppException("AGENT_SCOPE_INVALID", "科室智能体必须指定科室ID");
        }
        DepartmentInfo department = departmentManagementMapper.selectById(departmentId);
        if (department == null) {
            throw new AppException("DEPARTMENT_NOT_FOUND", "科室不存在");
        }
        Long hospitalId = department.getHospitalId() == null ? fallbackHospitalId : department.getHospitalId();
        HospitalInfo hospital = hospitalId == null ? null : hospitalManagementMapper.selectById(hospitalId);
        return KnowledgeAgentScope.builder()
                .agentType(KnowledgeAgentTypeConstants.DEPARTMENT)
                .hospitalId(hospital == null ? hospitalId : hospital.getId())
                .hospitalName(hospital == null ? department.getHospitalName() : hospital.getHospitalName())
                .departmentId(department.getId())
                .departmentName(department.getDepartmentName())
                .build();
    }

    private KnowledgeAgentScope resolveDoctor(Long doctorId) {
        if (doctorId == null || doctorId <= 0) {
            throw new AppException("AGENT_SCOPE_INVALID", "医生智能体必须指定医生ID");
        }
        DoctorInfo doctor = doctorManagementMapper.selectById(doctorId);
        if (doctor == null) {
            throw new AppException("DOCTOR_NOT_FOUND", "医生不存在");
        }
        DepartmentInfo department = doctor.getDepartmentId() == null ? null : departmentManagementMapper.selectById(doctor.getDepartmentId());
        HospitalInfo hospital = doctor.getHospitalId() == null ? null : hospitalManagementMapper.selectById(doctor.getHospitalId());
        return KnowledgeAgentScope.builder()
                .agentType(KnowledgeAgentTypeConstants.DOCTOR)
                .hospitalId(hospital == null ? doctor.getHospitalId() : hospital.getId())
                .hospitalName(firstText(hospital == null ? null : hospital.getHospitalName(), doctor.getHospitalName()))
                .departmentId(department == null ? doctor.getDepartmentId() : department.getId())
                .departmentName(firstText(department == null ? null : department.getDepartmentName(), doctor.getDepartmentName()))
                .doctorId(doctor.getId())
                .doctorName(doctor.getDoctorName())
                .build();
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
