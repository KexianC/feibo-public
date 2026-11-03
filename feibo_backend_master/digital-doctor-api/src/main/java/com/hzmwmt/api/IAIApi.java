package com.hzmwmt.api;

import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAIApi
 * @Date: 2025/9/11 9:56
 */
public interface IAIApi {
    /**
     * 症状分析
     *
     * @param symptomAnalysisRequestDTO
     * @return SSE流响应
     */
    SseEmitter symptomAnalysis(@Valid SymptomAnalysisRequestDTO symptomAnalysisRequestDTO);

    /**
     * 分页查询AI对话列表 (游标分页)
     *
     * @param lastCursorId 游标id
     * @param size         每页数量
     * @return 对话列表
     */
    Response<ApiCursorPageData<QueryConversationResponseDTO>> queryConversations(Long lastCursorId,
                                                                                 @Min(value = 1, message = "每页数量不能小于1")
                                                                                 @Max(value = 100, message = "每页数量不能超过100")
                                                                                 Integer size);

    /**
     * 分页查询指定对话内的消息记录 (游标分页)
     *
     * @param conversationId 对话ID
     * @param lastCursorId   游标id
     * @param size           每页数量
     * @return 消息列表
     */
    Response<ApiCursorPageData<QueryAiMessageResponseDTO>> queryMessages(Long conversationId,
                                                                         Long lastCursorId,
                                                                         @Min(value = 1, message = "每页数量不能小于1")
                                                                         @Max(value = 100, message = "每页数量不能超过100")
                                                                         Integer size
    );

    /**
     * 查询模型首页配置
     *
     * @return 模型树
     */
    Response<List<AIModelConfigIndexResponseDTO>> queryAiModelConfigIndex();

    /**
     * 更新AI对话标题
     *
     * @param requestDTO 包含对话ID和新标题的请求体
     * @return 操作结果
     */
    Response<Void> updateConversationTitle(@Valid UpdateConversationTitleRequestDTO requestDTO);

    /**
     * 删除AI对话
     *
     * @param conversationId 要删除的对话ID
     * @return 操作结果
     */
    Response<Void> deleteConversation(@NotNull(message = "对话ID不能为空") Long conversationId);

    /**
     * 获取AI推荐搜索
     * @return
     */
    Response<List<String>> getAiRecommendSearch();
}

