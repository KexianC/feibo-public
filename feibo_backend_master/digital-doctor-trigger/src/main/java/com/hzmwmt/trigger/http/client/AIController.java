package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.IAIApi;
import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ChatProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIConversationEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import com.hzmwmt.domain.ai.service.IChatService;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.util.TimeAgoUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIController
 * @Date: 2025/9/11 10:03
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/ai/")
public class AIController implements IAIApi {

    @Resource
    private IChatService chatService;
    @Resource
    private IAuthService authService;


    @Override
    @RequestMapping(value = "symptomAnalysis", method = RequestMethod.POST)
    @OperationLog(operationType = "分析", description = "症状分析", recordParams = true)
    public SseEmitter symptomAnalysis(@RequestBody SymptomAnalysisRequestDTO symptomAnalysisRequestDTO) {
        AuthUserEntity authUserEntity = authService.queryValidatedUser();
        Boolean isAssistant = authService.isDoctorAssistant();

        return chatService.completions(
                ChatProcessAggregate.builder()
                        .userId(authUserEntity.getUserId())
                        .resourceUserTypeEnumVO(ResourceUserTypeEnumVO.getByCode(isAssistant))
                        .aiMessageEntity(
                                AIMessageEntity.builder()
                                        .content(symptomAnalysisRequestDTO.getContent())
                                        .aiChatRoleEnumVO(AIChatRoleEnumVO.USER)
                                        .aiModelConfigId(symptomAnalysisRequestDTO.getAiModelConfigId())
                                        .aiConversationId(symptomAnalysisRequestDTO.getConversationId())
                                        .build()
                        )
                        .isAllHistory(symptomAnalysisRequestDTO.getIsAllHistory())
                        .build()
        );
    }

    @Override
    @RequestMapping(value = "conversation", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询对话列表", recordParams = true)
    public Response<ApiCursorPageData<QueryConversationResponseDTO>> queryConversations(@RequestParam(value = "lastCursorId", required = false) Long lastCursorId, @RequestParam("size") Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询对话列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<AIConversationEntity> domainPage = chatService.queryAiConversationIndex(userId, lastCursorId, size);
        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryConversationResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(
                            ApiCursorPageData.<QueryConversationResponseDTO>builder()
                                    .hasNext(false)
                                    .nextCursorId(null)
                                    .data(null)
                                    .build()
                    ).build();
        }
        return Response.<ApiCursorPageData<QueryConversationResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryConversationResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(aiConversation ->
                                                QueryConversationResponseDTO.builder()
                                                        .aiConversationId(aiConversation.getAiConversationId())
                                                        .title(aiConversation.getTitle())
                                                        .createTime(TimeAgoUtil.format(aiConversation.getCreateTime()))
                                                        .build()
                                        ).toList())
                                .build()
                ).build();
    }

    @Override
    @RequestMapping(value = "conversation/{conversationId}/messages", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询对话消息", recordParams = true)
    public Response<ApiCursorPageData<QueryAiMessageResponseDTO>> queryMessages(@PathVariable("conversationId") Long conversationId, @RequestParam(value = "lastCursorId", required = false) Long lastCursorId, @RequestParam("size") Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询对话 {} 的消息列表, lastCursorId: {}, size: {}", userId, conversationId, lastCursorId, size);
        CursorPageResponseData<AIMessageEntity> domainPage = chatService.queryAiMessageIndex(userId, conversationId, lastCursorId, size);
        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryAiMessageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(
                            ApiCursorPageData.<QueryAiMessageResponseDTO>builder()
                                    .hasNext(false)
                                    .nextCursorId(null)
                                    .data(null)
                                    .build()
                    ).build();
        }
        return Response.<ApiCursorPageData<QueryAiMessageResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryAiMessageResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(
                                                aiMessage ->
                                                        QueryAiMessageResponseDTO.builder()
                                                                .role(aiMessage.getAiChatRoleEnumVO().getCode())
                                                                .content(aiMessage.getContent())
                                                                .createTime(TimeAgoUtil.format(aiMessage.getCreateTime()))
                                                                .build()
                                        ).toList()
                                )
                                .build()
                )
                .build();

    }

    @Override
    @RequestMapping(value = "model/index", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询AI模型配置列表", recordParams = true)
    public Response<List<AIModelConfigIndexResponseDTO>> queryAiModelConfigIndex() {
        List<AIModelConfigAggregate> aiModelConfigAggregates = chatService.queryAiModelConfigIndex();
        if (aiModelConfigAggregates == null) {
            return Response.<List<AIModelConfigIndexResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(null)
                    .build();
        }

        List<AIModelConfigIndexResponseDTO> responseList = aiModelConfigAggregates.stream()
                .map(this::treeToConvert)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Response.<List<AIModelConfigIndexResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseList)
                .build();
    }

    /**
     * 递归转换聚合对象为响应DTO
     */
    private AIModelConfigIndexResponseDTO treeToConvert(AIModelConfigAggregate aggregate) {
        if (aggregate == null || aggregate.getData() == null) {
            return null;
        }

        AIModelConfigEntity entity = aggregate.getData();

        // 递归转换子节点
        List<AIModelConfigIndexResponseDTO> childrenList = null;
        if (aggregate.getChildrenEntityList() != null && !aggregate.getChildrenEntityList().isEmpty()) {
            childrenList = aggregate.getChildrenEntityList().stream()
                    .map(this::treeToConvert)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 如果转换后的子节点列表为空，设置为null而不是空列表
            if (childrenList.isEmpty()) {
                childrenList = null;
            }
        }

        return AIModelConfigIndexResponseDTO.builder()
                .aiModelConfigId(entity.getAiModelConfigId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .image(entity.getImage())
                .childrenList(childrenList)
                .build();
    }


    @Override
    @RequestMapping(value = "conversation/update-title", method = RequestMethod.PUT)
    @OperationLog(operationType = "更新", description = "更新对话标题", recordParams = true)
    public Response<Void> updateConversationTitle(@RequestBody UpdateConversationTitleRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 更新对话 {} 的标题，标题长度: {}", userId,
                requestDTO.getAiConversationId(),
                requestDTO.getTitle() == null ? 0 : requestDTO.getTitle().length());

        AIConversationEntity entity = AIConversationEntity.builder()
                .userId(userId)
                .aiConversationId(requestDTO.getAiConversationId())
                .title(requestDTO.getTitle())
                .build();

        chatService.updateAiConversationTitle(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "conversation/{conversationId}/delete", method = RequestMethod.POST)
    @OperationLog(operationType = "删除", description = "删除对话", recordParams = true)
    public Response<Void> deleteConversation(@PathVariable("conversationId") Long conversationId) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 删除对话 {}", userId, conversationId);

        AIConversationEntity entity = AIConversationEntity.builder()
                .userId(userId)
                .aiConversationId(conversationId)
                .build();

        chatService.deleteAiConversation(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @RequestMapping(value = "conversation/recommend-search", method = RequestMethod.GET)
    @OperationLog(operationType = "获取AI推荐搜索", description = "获取AI推荐搜索")
    public Response<List<String>> getAiRecommendSearch() {
        return Response.<List<String>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(chatService.getAiRecommendSearch())
                .build();
    }
}
