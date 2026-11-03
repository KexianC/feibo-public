package com.hzmwmt.api;

import com.hzmwmt.api.dto.QueryNotifyMessageResponseDTO;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IMessageApi
 * @Date: 2025/9/12 9:22
 */
public interface IMessageApi {

    Response<ApiCursorPageData<QueryNotifyMessageResponseDTO>> queryNotifyMessages(
            Long lastCursorId,
            @Max(value = 100, message = "分页大小不能大于100")
            @Min(value = 1, message = "分页大小不能小于1")
            Integer size
    );
}
