package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMessageMapper
 * @Date: 2025/8/11 11:41
 */
public interface AIMessageMapper extends BaseMapper<AIMessage> {


    List<AIMessage> selectAiMessageListCursorPageByConversationIdAndUserId(@Param("userId") Long userId,
                                                                           @Param("aiConversationId") Long aiConversationId,
                                                                           @Param("lastCursorId") Long lastCursorId,
                                                                           @Param("size") Integer size);


    List<AIMessage> selectAiMessageHistoryByConversationIdAndUserIdAndAiModelConfigId(@Param("userId") Long userId,
                                                                                       @Param("aiConversationId") Long aiConversationId,
                                                                                       @Param("aiModelConfigId") Long aiModelConfigId,
                                                                                       @Param("isAllHistory") Boolean isAllHistory);

}
