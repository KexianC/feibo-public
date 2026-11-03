package com.hzmwmt.infrastructure.gateway.ai.tencent;

import com.hzmwmt.infrastructure.gateway.ai.tencent.dto.ChatRequestDTO;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITencentAgentApiService
 * @Date: 2025/9/11 16:03
 */

public interface ITencentAgentApiService {

    /**
     * 定义对腾讯云智能体 SSE 接口的调用
     */
    @Streaming
    @POST("/v1/qbot/chat/sse")
    Call<ResponseBody> streamChat(@Body ChatRequestDTO chatRequestDTO);
}