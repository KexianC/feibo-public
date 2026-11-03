package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.DepartmentStatisticsResponseDTO;
import com.hzmwmt.model.entity.DepartmentInfo;
import com.hzmwmt.service.DepartmentManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/department")
public class DepartmentManagementController {

    @Resource
    private DepartmentManagementService departmentManagementService;

    @PostMapping("/addDepartment")
    public Result addDepartment(@RequestBody DepartmentInfo departmentInfo) {
        if (departmentInfo == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "科室信息不能为空");
        }
        if (StringUtils.isBlank(departmentInfo.getDepartmentName())) {
            throw new AppException("ILLEGAL_ARGUMENT", "科室名称不能为空");
        }
        departmentManagementService.addDepartment(departmentInfo);
        return Result.success();
    }

    @PostMapping("/editDepartment")
    public Result editDepartment(@RequestBody DepartmentInfo departmentInfo) {
        if (departmentInfo == null || departmentInfo.getId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "科室ID不能为空");
        }
        departmentManagementService.editDepartment(departmentInfo);
        return Result.success();
    }

    @DeleteMapping("/deleteDepartment")
    public Result deleteDepartment(@RequestParam("id") Long id) {
        if (id == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "科室ID不能为空");
        }
        departmentManagementService.deleteDepartment(id);
        return Result.success();
    }

    @GetMapping("/page")
    public PageResult<DepartmentInfo> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "departmentName", required = false) String departmentName,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "statue", required = false) Integer statue) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于0");
        }
        Page<DepartmentInfo> page = new Page<>(pageNum, pageSize);
        IPage<DepartmentInfo> deptPage = departmentManagementService.page(page, departmentName, hospitalId,statue);
        return PageResult.of(deptPage);
    }

    @GetMapping("/statistics")
    public Result<DepartmentStatisticsResponseDTO> statistics(
            @RequestParam(value = "departmentName", required = false) String departmentName,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "statue", required = false) Integer statue
    ){
        DepartmentStatisticsResponseDTO statisticsResponseDTO = departmentManagementService.statistics(departmentName,hospitalId,statue);
        return Result.success(statisticsResponseDTO);
    }

    @PostMapping("/toggleStatue")
    public Result toggleStatue(
            @RequestParam(value = "id")Long id,
            @RequestParam(value = "statue")Integer statue
    ){
        if (id==null||statue==null){
            throw new AppException("ILLEGAL_ARGUMENT", "必须传入有效的id和statue！");
        }
        departmentManagementService.toggleStatue(id,statue);
        return Result.success();
    }

    @GetMapping("/all")
    public Result<List<DepartmentInfo>> all(
            @RequestParam(value = "id")Long id
    ){
        return Result.success(departmentManagementService.all(id));
    }
}
