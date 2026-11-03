package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.model.dto.HospitalStatisticsResponseDTO;
import com.hzmwmt.model.entity.HospitalInfo;

import java.util.List;

public interface HospitalManagementService {
    void addHospital(HospitalInfo hospitalInfo);
    void editHospital(HospitalInfo hospitalInfo);
    void deleteHospital(Long id);
    IPage<HospitalInfo> page(Page<HospitalInfo> page, String hospitalName, String hospitalLevel, String hospitalType, Integer statue);

    HospitalStatisticsResponseDTO statistics(String hospitalName, String hospitalLevel, String hospitalType,Integer statue);

    void toggleStatue(Long id, Integer statue);

    List<HospitalInfo> all();
}