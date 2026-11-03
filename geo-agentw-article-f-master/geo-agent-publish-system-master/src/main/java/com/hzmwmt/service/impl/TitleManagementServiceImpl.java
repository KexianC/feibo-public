package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.mapper.TitleManagementMapper;
import com.hzmwmt.model.dto.TitleStatisticResponseDTO;
import com.hzmwmt.model.entity.TitleInfo;
import com.hzmwmt.service.TitleManagementService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TitleManagementServiceImpl implements TitleManagementService {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private TitleManagementMapper titleManagementMapper;

    @Override
    public void addTitle(TitleInfo titleInfo) {
        titleInfo.setId(snowflakeIdGenerator.nextId());
        if (titleInfo.getEnableStatus() == null) {
            titleInfo.setEnableStatus(1);
        }
        if (titleInfo.getTitleLevel() == null) {
            titleInfo.setTitleLevel(0);
        }
        int rows = titleManagementMapper.insert(titleInfo);
        if (rows != 1) {
            throw new RuntimeException("新增职称失败，数据库影响行数异常");
        }
    }

    @Override
    public void editTitle(TitleInfo titleInfo) {
        int rows = titleManagementMapper.updateById(titleInfo);
        if (rows != 1) {
            throw new RuntimeException("更新职称信息失败，该职称可能不存在");
        }
    }

    @Override
    public void deleteTitle(Long id) {
        int rows = titleManagementMapper.deleteById(id);
        if (rows != 1) {
            throw new RuntimeException("删除失败");
        }
    }

    @Override
    public IPage<TitleInfo> page(Page<TitleInfo> page, String titleName, Integer level,Integer statue) {
        LambdaQueryWrapper<TitleInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(titleName != null && !titleName.isEmpty(), TitleInfo::getTitleName, titleName);
        queryWrapper.eq(level != null, TitleInfo::getTitleLevel,level);
        queryWrapper.eq(statue != null, TitleInfo::getEnableStatus, statue);
        return titleManagementMapper.selectPage(page, queryWrapper);
    }

    @Override
    public TitleStatisticResponseDTO stastics(String titleName, Integer level, Integer statue) {
        TitleStatisticResponseDTO res = new TitleStatisticResponseDTO();

        LambdaQueryWrapper<TitleInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(titleName != null && !titleName.isEmpty(), TitleInfo::getTitleName, titleName);
        queryWrapper.eq(level != null, TitleInfo::getTitleLevel,level);
        queryWrapper.eq(statue != null, TitleInfo::getEnableStatus, statue);

        res.setTotal(titleManagementMapper.selectCount(queryWrapper));
        res.setEnabled(titleManagementMapper.selectCount(queryWrapper.clone().eq(TitleInfo::getEnableStatus,1)));
        res.setDisabled(titleManagementMapper.selectCount(queryWrapper.clone().eq(TitleInfo::getEnableStatus,0)));
        // 最大等级查询
        QueryWrapper<TitleInfo> maxWrapper = new QueryWrapper<>();
        if (titleName != null && !titleName.isEmpty()) {
            maxWrapper.like("title_name", titleName);
        }
        if (level != null) {
            maxWrapper.eq("title_level", level);
        }
        if (statue != null) {
            maxWrapper.eq("enable_status", statue);
        }
        maxWrapper.select("MAX(title_level) AS maxLevel");
        List<Map<String, Object>> maps = titleManagementMapper.selectMaps(maxWrapper);
        Long maxLevel = null;
        if (maps != null && !maps.isEmpty()) {
            Map<String, Object> map = maps.get(0);
            if (map != null) {
                Object value = map.get("maxLevel");
                if (value instanceof Number) {
                    maxLevel = ((Number) value).longValue();
                }
            }
        }
        res.setMaxLevel(maxLevel);
        return res;
    }

    @Override
    public void toggleStatue(Long id, Integer statue) {
        LambdaUpdateWrapper<TitleInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(id!=null,TitleInfo::getId,id)
                .set(TitleInfo::getEnableStatus,statue);
        titleManagementMapper.update(updateWrapper);
    }

    @Override
    public List<TitleInfo> all() {
        LambdaQueryWrapper<TitleInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(TitleInfo::getEnableStatus,1);
        List<TitleInfo> list = titleManagementMapper.selectList(qw);
        return list;
    }
}