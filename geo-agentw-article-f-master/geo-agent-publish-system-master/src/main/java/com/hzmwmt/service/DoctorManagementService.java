package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.model.dto.DoctorStatisticsResponseDTO;
import com.hzmwmt.model.entity.DoctorInfo;

import java.util.List;

public interface DoctorManagementService {
    void addDoctor(DoctorInfo doctorInfo);

    void editDoctor(DoctorInfo doctorInfo);

    void deleteDoctor(Long id);

    IPage<DoctorInfo>
    page(Page<DoctorInfo> page, String doctorName, Long hospital, Long department, Long title, Integer statue);

    DoctorStatisticsResponseDTO stastics(String doctorName, Long hospital, Long department, Long title, Integer statue);

    void toggleStatue(Long id, Integer statue);

    List<DoctorInfo> all();

    IPage<DoctorInfo> pageOther(Page<DoctorInfo> page, String doctorName, Long hospital, Long department, Long title, Integer knowledgeStatue);
}
