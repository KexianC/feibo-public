package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.DoctorStatisticsResponseDTO;
import com.hzmwmt.model.dto.TitleStatisticResponseDTO;
import com.hzmwmt.model.entity.TitleInfo;
import com.hzmwmt.service.TitleManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/title")
public class TitleManagementController {

    @Resource
    private TitleManagementService titleManagementService;

    @PostMapping("/addTitle")
    public Result addTitle(@RequestBody TitleInfo titleInfo) {
        if (titleInfo == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "职称信息不能为空");
        }
        if (StringUtils.isBlank(titleInfo.getTitleName())) {
            throw new AppException("ILLEGAL_ARGUMENT", "职称名称不能为空");
        }
        titleManagementService.addTitle(titleInfo);
        return Result.success();
    }

    @PostMapping("/editTitle")
    public Result editTitle(@RequestBody TitleInfo titleInfo) {
        if (titleInfo == null || titleInfo.getId() == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "职称ID不能为空");
        }
        titleManagementService.editTitle(titleInfo);
        return Result.success();
    }

    @DeleteMapping("/deleteTitle")
    public Result deleteTitle(@RequestParam("id") Long id) {
        if (id == null) {
            throw new AppException("ILLEGAL_ARGUMENT", "职称ID不能为空");
        }
        titleManagementService.deleteTitle(id);
        return Result.success();
    }

    @GetMapping("/page")
    public PageResult<TitleInfo> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "titleName", required = false) String titleName,
            @RequestParam(value = "titleLevel", required = false) Integer level,
            @RequestParam(value = "statue", required = false) Integer statue) {
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于0");
        }
        Page<TitleInfo> page = new Page<>(pageNum, pageSize);
        IPage<TitleInfo> titlePage = titleManagementService.page(page, titleName, level, statue);
        return PageResult.of(titlePage);
    }

    @GetMapping("/statistics")
    public Result<TitleStatisticResponseDTO> statistics(
            @RequestParam(value = "titleName", required = false) String titleName,
            @RequestParam(value = "titleLevel", required = false) Integer level,
            @RequestParam(value = "statue", required = false) Integer statue
    ){
        TitleStatisticResponseDTO statisticsResponseDTO = titleManagementService.stastics(titleName, level,statue);
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
        titleManagementService.toggleStatue(id,statue);
        return Result.success();
    }

    @GetMapping("/all")
    public Result<List<TitleInfo>> all(){
        return Result.success(titleManagementService.all());
    }
}
