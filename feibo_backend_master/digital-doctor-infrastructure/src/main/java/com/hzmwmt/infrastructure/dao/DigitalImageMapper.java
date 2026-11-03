package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalImage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalImageMapper
 * @Date: 2025/8/22 9:43
 */
public interface DigitalImageMapper extends BaseMapper<DigitalImage> {


    List<DigitalImage> selectUserBackgroundImageListCursorPage(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);


    List<DigitalImage> selectUserIllustrationImageListCursorPage(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);


    List<DigitalImage> selectSysBackgroundImageListCursorPage(@Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);


    List<DigitalImage> selectSysIllustrationImageListCursorPage(@Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);
}
