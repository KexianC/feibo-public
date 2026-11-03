package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalVoice;

import java.util.List;

/**
 * DigitalVoiceMapper 接口
 */
public interface DigitalVoiceMapper extends BaseMapper<DigitalVoice> {

    /**
     * 按游标分页查询语音列表
     */
    List<DigitalVoice> selectVoiceListCursorPage(Long lastCursorId, Integer size);

    /**
     * 根据语音ID列表查询语音
     */
    List<DigitalVoice> selectVoiceListByVoiceIds(List<Long> voiceIds);

    /**
     * 复合条件查询语音列表
     */
    List<DigitalVoice> selectVoiceListByCompoundQuery(List<Long> voiceIdList, Long lastCursorId, Integer gender, String name, Integer size);
}