package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalVoiceCategory;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryMapper
 * @Date: 2025/9/25 13:10
 */
public interface DigitalVoiceCategoryMapper extends BaseMapper<DigitalVoiceCategory> {
    List<DigitalVoiceCategory> selectDigitalVoiceCategoryByType(Integer type);
}