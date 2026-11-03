package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.DoctorStatisticsResponseDTO;
import com.hzmwmt.model.entity.DoctorInfo;
import com.hzmwmt.service.DoctorManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doctor")
public class DoctorManagementController {

    @Resource
    private DoctorManagementService doctorManagementService;

    @PostMapping("/addDoctor")
    private Result addDoctor(@RequestBody DoctorInfo doctorInfo) {
        if (doctorInfo == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医生信息不能为空");
        }
        if (StringUtils.isBlank(doctorInfo.getDoctorName())) {
            throw new AppException("ILLEGAL_ARGUMENT", "医生姓名不能为空");
        }
        if (doctorInfo.getHospitalId() == null || doctorInfo.getHospitalId() <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "医院ID不合法");
        }
        if (doctorInfo.getDepartmentId() == null || doctorInfo.getDepartmentId() <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "科室ID不合法");
        }
        doctorManagementService.addDoctor(doctorInfo);
        return Result.success();
    }

    @PostMapping("/editDoctor")
    private Result editDoctor(@RequestBody DoctorInfo doctorInfo) {
        if (doctorInfo == null || doctorInfo.getId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医生ID不能为空");
        }
        doctorManagementService.editDoctor(doctorInfo);
        return Result.success();
    }

    @DeleteMapping("/deleteDoctor")
    private Result deleteDoctor(@RequestParam("id") Long id) {
        if (id == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医生ID不能为空");
        }
        doctorManagementService.deleteDoctor(id);
        return Result.success();
    }

    @GetMapping("/page")
    private PageResult<DoctorInfo> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "hospital", required = false)Long hospital,
            @RequestParam(value = "department", required = false)Long department,
            @RequestParam(value = "title", required = false)Long title,
            @RequestParam(value = "statue", required = false)Integer statue
    ) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于 0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于 0");
        }
        Page<DoctorInfo> page = new Page(pageNum ,pageSize);
        IPage<DoctorInfo> doctorInfos = doctorManagementService.page(page, doctorName, hospital, department, title, statue);
        return PageResult.of(doctorInfos);
    }

    @GetMapping("/pageOther")
    private PageResult<DoctorInfo> pageOther(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "hospital", required = false)Long hospital,
            @RequestParam(value = "department", required = false)Long department,
            @RequestParam(value = "title", required = false)Long title,
            @RequestParam(value = "knowledgeStatue",required = false)Integer knowledgeStatue
    ) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于 0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于 0");
        }
        Page<DoctorInfo> page = new Page(pageNum ,pageSize);
        IPage<DoctorInfo> doctorInfos = doctorManagementService.pageOther(page, doctorName, hospital, department, title,knowledgeStatue);
        return PageResult.of(doctorInfos);
    }

    @GetMapping("/statistics")
    public Result<DoctorStatisticsResponseDTO> statistics(
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "hospital", required = false)Long hospital,
            @RequestParam(value = "department", required = false)Long department,
            @RequestParam(value = "title", required = false)Long title,
            @RequestParam(value = "statue", required = false)Integer statue
    ){
        DoctorStatisticsResponseDTO statisticsResponseDTO = doctorManagementService.stastics(doctorName,hospital,department,title,statue);
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
        doctorManagementService.toggleStatue(id,statue);
        return Result.success();
    }

    @GetMapping("/all")
    public Result<List<DoctorInfo>> all(){
        return Result.success(doctorManagementService.all());
    }

}
