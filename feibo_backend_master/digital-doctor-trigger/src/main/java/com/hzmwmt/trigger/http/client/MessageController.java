package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.IMessageApi;
import com.hzmwmt.api.dto.QueryNotifyMessageResponseDTO;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.message.model.entity.MessageEntity;
import com.hzmwmt.domain.message.service.IMessageService;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName MessageController
 * @Date: 2025/9/12 9:26
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/message/")
public class MessageController implements IMessageApi {
    @Resource
    private IMessageService messageService;
    @Resource
    private IAuthService authService;

    @Override
    @RequestMapping(value = "notify/list")
    // @OperationLog(operationType = "查询", description = "查询通知消息列表", recordParams = true)
    public Response<ApiCursorPageData<QueryNotifyMessageResponseDTO>> queryNotifyMessages(@RequestParam(required = false) Long lastCursorId, @RequestParam Integer size) {
        try {
            // 验证用户认证状态
            Long userId = authService.queryValidatedUser().getUserId();

            // 验证size参数
            if (size == null || size <= 0 || size > 50) {
                return Response.<ApiCursorPageData<QueryNotifyMessageResponseDTO>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("参数size必须在1-50之间")
                        .build();
            }

            // 查询消息列表
            CursorPageResponseData<MessageEntity> messageEntityCursorPageResponseData = messageService.queryReadMessage(userId, lastCursorId, size);

            if (messageEntityCursorPageResponseData.getData() == null) {
                return Response.<ApiCursorPageData<QueryNotifyMessageResponseDTO>>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(ApiCursorPageData.<QueryNotifyMessageResponseDTO>builder()
                                .data(null)
                                .hasNext(false)
                                .nextCursorId(null)
                                .build())
                        .build();
            }

            return Response.<ApiCursorPageData<QueryNotifyMessageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryNotifyMessageResponseDTO>builder()
                            .data(messageEntityCursorPageResponseData.getData().stream().map(messageEntity ->
                                            QueryNotifyMessageResponseDTO.builder()
                                                    .title(messageEntity.getTitle())
                                                    .content(messageEntity.getContent())
                                                    .sendTime(TimeAgoUtil.format(messageEntity.getSendTime()))
                                                    .build()
                                    ).toList()
                            )
                            .hasNext(messageEntityCursorPageResponseData.getHasNext())
                            .nextCursorId(messageEntityCursorPageResponseData.getNextCursorId())
                            .build()
                    )
                    .build();
        } catch (IllegalStateException e) {
            // 处理用户未认证的情况
            return Response.<ApiCursorPageData<QueryNotifyMessageResponseDTO>>builder()
                    .code(ResponseCode.E0001.getCode())
                    .info(ResponseCode.E0001.getInfo())
                    .build();
        } catch (Exception e) {
            // 处理其他异常
            log.error("查询通知消息列表失败，异常类型: {}", e.getClass().getSimpleName());
            return Response.<ApiCursorPageData<QueryNotifyMessageResponseDTO>>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info("系统异常，请稍后重试")
                    .build();
        }

    }
}
