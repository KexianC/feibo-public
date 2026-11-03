package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.PushMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PushMessageMapper
 * @Date: 2025/9/2 16:33
 */
public interface PushMessageMapper extends BaseMapper<PushMessage> {

    List<PushMessage> selectUnreadListByUserId(@Param("userId") Long userId);

    List<PushMessage> selectReadListCursorPageByUserId(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);

    void updatePushMessage2Read(@Param("pushMessageId") Long pushMessageId);
}