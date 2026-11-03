package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.HospitalStatisticsResponseDTO;
import com.hzmwmt.model.entity.HospitalInfo;
import com.hzmwmt.service.HospitalManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hospital")
public class HospitalManagementController {

    @Resource
    private HospitalManagementService hospitalManagementService;

    @PostMapping("/addHospital")
    public Result addHospital(@RequestBody HospitalInfo hospitalInfo) {
        if (hospitalInfo == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医院信息不能为空");
        }
        if (StringUtils.isBlank(hospitalInfo.getHospitalName())) {
            throw new AppException("ILLEGAL_ARGUMENT", "医院名称不能为空");
        }
        hospitalManagementService.addHospital(hospitalInfo);
        return Result.success();
    }

    @PostMapping("/editHospital")
    public Result editHospital(@RequestBody HospitalInfo hospitalInfo) {
        if (hospitalInfo == null || hospitalInfo.getId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医院ID不能为空");
        }
        hospitalManagementService.editHospital(hospitalInfo);
        return Result.success();
    }

    @DeleteMapping("/deleteHospital")
    public Result deleteHospital(@RequestParam("id") Long id) {
        if (id == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "医院ID不能为空");
        }
        hospitalManagementService.deleteHospital(id);
        return Result.success();
    }

    @GetMapping("/page")
    public PageResult<HospitalInfo> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "hospitalName", required = false) String hospitalName,
            @RequestParam(value = "hospitalLevel", required = false) String hospitalLevel,
            @RequestParam(value = "hospitalType", required = false) String hospitalType,
            @RequestParam(value = "statue", required = false) Integer statue) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于0");
        }
        Page<HospitalInfo> page = new Page<>(pageNum, pageSize);
        IPage<HospitalInfo> hospitalPage = hospitalManagementService.page(page, hospitalName, hospitalLevel, hospitalType,statue);
        return PageResult.of(hospitalPage);
    }

    @GetMapping("/statistics")
    public Result<HospitalStatisticsResponseDTO> page(
            @RequestParam(value = "hospitalName", required = false) String hospitalName,
            @RequestParam(value = "hospitalLevel", required = false) String hospitalLevel,
            @RequestParam(value = "hospitalType", required = false) String hospitalType,
            @RequestParam(value = "statue", required = false) Integer statue) {
        HospitalStatisticsResponseDTO statisticsResponseDTO = new HospitalStatisticsResponseDTO();
        statisticsResponseDTO = hospitalManagementService.statistics(hospitalName, hospitalLevel,hospitalType,statue);
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
        hospitalManagementService.toggleStatue(id,statue);
        return Result.success();
    }

    @GetMapping("/all")
    public Result<List<HospitalInfo>> all(){
        return Result.success(hospitalManagementService.all());
    }
}
