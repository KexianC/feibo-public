package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.model.entity.KnowledgeVectorConversion;
import com.hzmwmt.service.KnowledgeVectorConversionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conversion")
public class KnowledgeVectorConversionController {
    @Resource
    KnowledgeVectorConversionService knowledgeVectorConversionService;

    @GetMapping("/page")
    public PageResult<KnowledgeVectorConversion> page(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "agentType", required = false) Integer agentType,
            @RequestParam(value = "hospitalId", required = false) Long hospitalId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "doctorId", required = false) Long doctorId,
            @RequestParam(value = "fileName", required = false)String fileName,
            @RequestParam(value = "status", required = false) Integer status
    ){
        if (pageNum == null || pageNum <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "页码必须大于0");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new AppException("ILLEGAL_ARGUMENT", "分页大小必须大于0");
        }
        Page<KnowledgeVectorConversion> page = new Page<>(pageNum, pageSize);
        IPage<KnowledgeVectorConversion> result = knowledgeVectorConversionService.page(page, agentType, hospitalId,
                departmentId, doctorId, fileName, status);
        return PageResult.of(result);
    }
}
