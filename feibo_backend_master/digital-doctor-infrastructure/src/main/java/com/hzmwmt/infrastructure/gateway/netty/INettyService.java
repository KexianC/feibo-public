package com.hzmwmt.infrastructure.gateway.netty;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName INettyService
 * @Date: 2025/9/3 17:08
 */
public interface INettyService {

    Boolean sendMessageToUser(Long userId, String title, String content, String createTime);
}
