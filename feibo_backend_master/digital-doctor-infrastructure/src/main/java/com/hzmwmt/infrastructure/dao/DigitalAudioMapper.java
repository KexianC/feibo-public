package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.DigitalAudio;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalAudioMapper
 * @Date: 2025/8/22 9:44
 */
public interface DigitalAudioMapper extends BaseMapper<DigitalAudio> {

    /**
     * 查询用户音乐列表（游标分页）
     */
    @Select("SELECT digital_audio_id, user_id, name, url, duration, type, create_time, update_time " +
            "FROM t_digital_audio " +
            "WHERE user_id = #{userId} and type = 2 " +
            "${lastCursorId != null ? 'AND digital_audio_id < ' + lastCursorId : ''} " +
            "ORDER BY digital_audio_id DESC LIMIT #{size}")
    List<DigitalAudio> selectUserMusicListCursorPage(@Param("userId") Long userId,
                                                     @Param("lastCursorId") Long lastCursorId,
                                                     @Param("size") Integer size);

    /**
     * 查询用户录音列表（游标分页）
     */
    @Select("SELECT digital_audio_id, user_id, 'temp' as name, url, duration, type, create_time, update_time " +
            "FROM t_digital_audio " +
            "WHERE user_id = #{userId} and type = 1 " +
            "${lastCursorId != null ? 'AND digital_audio_id < ' + lastCursorId : ''} " +
            "ORDER BY digital_audio_id DESC LIMIT #{size}")
    List<DigitalAudio> selectUserRecordingListCursorPage(@Param("userId") Long userId,
                                                         @Param("lastCursorId") Long lastCursorId,
                                                         @Param("size") Integer size);

    /**
     * 查询系统音乐列表（游标分页）
     */
    @Select("SELECT digital_audio_id, user_id, name, url, duration, type, create_time, update_time " +
            "FROM t_digital_audio " +
            "WHERE user_id IS NULL and type = 2 " +
            "${lastCursorId != null ? 'AND digital_audio_id < ' + lastCursorId : ''} " +
            "ORDER BY digital_audio_id DESC LIMIT #{size}")
    List<DigitalAudio> selectSysMusicListCursorPage(@Param("lastCursorId") Long lastCursorId,
                                                    @Param("size") Integer size);
}