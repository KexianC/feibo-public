package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.AIConversation;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIConversationMapper
 * @Date: 2025/8/11 11:40
 */
public interface AIConversationMapper extends BaseMapper<AIConversation> {

    List<AIConversation> selectAiConversationListCursorPageByUserIdAndModel(@Param("userId") Long userId, @Param("lastCursorId") Long lastCursorId, @Param("size") Integer size);

    int updateConversationByAiConversationIdAndUserId(AIConversation conversation);

    Boolean validateAiConversationIdAndUserId(@Param("userId") Long userId,
                                              @Param("aiConversationId") Long aiConversationId);

    Long selectConsumptionByAiConversationIdAndUserId(@Param("userId") Long userId,
                                                       @Param("aiConversationId") Long aiConversationId);

    BigDecimal selectBillingByAiConversationIdAndUserId(@Param("userId") Long userId,
                                                        @Param("aiConversationId") Long aiConversationId);
}
