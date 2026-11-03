package com.hzmwmt.infrastructure.gateway.netty.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName NettyMessageRequestDTO
 * @Date: 2025/9/11 22:39
 */
@Builder
@Data
public class NettyMessageRequestDTO {
    private Long userId;
    private String title;
    private String content;
    private String createTime;
}
