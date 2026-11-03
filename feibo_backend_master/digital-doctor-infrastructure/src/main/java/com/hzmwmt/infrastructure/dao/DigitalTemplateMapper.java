package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalTemplate;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalTemplateMapper
 * @Date: 2025/9/24 21:00
 */
public interface DigitalTemplateMapper extends BaseMapper<DigitalTemplate> {
    List<DigitalTemplate> selectUserTemplateListCursorPage(Long userId, Long lastCursorId, int size);

    List<DigitalTemplate> selectSysTemplateListCursorPage(Long lastCursorId, int size);
}
