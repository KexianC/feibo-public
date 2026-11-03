package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalVideo;
import com.hzmwmt.infrastructure.dao.po.DigitalVoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVideoMapper
 * @Date: 2025/8/22 9:44
 */
@Mapper
public interface DigitalVideoMapper extends BaseMapper<DigitalVideo> {


    List<DigitalVideo> selectUserVideoListCursorPage(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);


    List<DigitalVideo> selectSysVideoListCursorPage(@Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);

    /**
     * 复合查询语音列表（游标分页）
     */
    List<DigitalVoice> selectVoiceListByCompoundQuery(
            @Param("ids") List<Long> ids,
            @Param("genderCode") Integer genderCode,
            @Param("name") String name,
            @Param("lastCursorId") Long lastCursorId,
            @Param("limit") Integer limit
    );

    List<DigitalVoice> selectVoiceListCursorPage(Long lastCursorId, int i);

    List<DigitalVoice> selectVoiceListByVoiceIds(List<Long> voiceCollectedIds);

    List<DigitalVoice> selectVoiceListByCompoundQuery(java.util.List<java.lang.Long> list, Boolean code, String name, Long lastCursorId, int limit);
}
