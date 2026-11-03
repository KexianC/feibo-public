package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.model.dto.DepartmentStatisticsResponseDTO;
import com.hzmwmt.model.entity.DepartmentInfo;

import java.util.List;

public interface DepartmentManagementService {
    void addDepartment(DepartmentInfo departmentInfo);
    void editDepartment(DepartmentInfo departmentInfo);
    void deleteDepartment(Long id);
    IPage<DepartmentInfo> page(Page<DepartmentInfo> page, String departmentName, Long hospitalId, Integer statue);

    void toggleStatue(Long id, Integer statue);

    List<DepartmentInfo> all(Long id);

    DepartmentStatisticsResponseDTO statistics(String departmentName, Long hospitalId, Integer statue);
}