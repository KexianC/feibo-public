package com.hzmwmt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.TitleStatisticResponseDTO;
import com.hzmwmt.model.entity.TitleInfo;

import java.util.List;

public interface TitleManagementService {
    void addTitle(TitleInfo titleInfo);
    void editTitle(TitleInfo titleInfo);
    void deleteTitle(Long id);
    IPage<TitleInfo> page(Page<TitleInfo> page, String titleName, Integer level,Integer statue);

    TitleStatisticResponseDTO stastics(String titleName, Integer level, Integer statue);

    void toggleStatue(Long id, Integer statue);

    List<TitleInfo> all();
}