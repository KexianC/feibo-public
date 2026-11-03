package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalVoiceCategoryMap;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryMapMapper
 * @Date: 2025/9/25 13:14
 */
public interface DigitalVoiceCategoryMapMapper extends BaseMapper<DigitalVoiceCategoryMap> {

    List<Long> selectDigitalVoiceIdsByDigitalVoiceCategoryIdInDigitalVoiceIds(
            @Param("digitalVoiceCategoryId") Long digitalVoiceCategoryId,
            @Param("digitalVoiceIds") List<Long> digitalVoiceIds
    );
}
