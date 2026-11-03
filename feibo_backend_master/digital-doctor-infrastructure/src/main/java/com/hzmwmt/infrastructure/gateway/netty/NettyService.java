package com.hzmwmt.infrastructure.gateway.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.infrastructure.gateway.netty.server.UserChannelManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName NettyService
 * @Date: 2025/8/29 17:32
 */
@Slf4j
@Service
public class NettyService implements INettyService {
    @Resource
    ObjectMapper objectMapper;

    @Override
    public Boolean sendMessageToUser(Long userId, String title, String content, String createTime) {
        try {
            // 检查参数是否为空
            if (userId == null) {
                log.error("WebSocket消息发送失败：用户ID为空");
                return false;
            }

            // 创建消息对象，确保字段名称符合前端预期
            Map<String, Object> message = new HashMap<>();
            message.put("title", title != null ? title : "");
            message.put("content", content != null ? content : "");
            message.put("createTime", createTime != null ? createTime : "");
            message.put("type", "notification"); // 添加消息类型标识
            message.put("status", "success");    // 添加状态标识

            log.info("准备发送WebSocket消息，用户ID: {}, fieldCount: {}", userId, message.size());

            // 序列化消息
            String jsonMessage = objectMapper.writeValueAsString(message);

            // 序列化后检查JSON字符串
            if (jsonMessage == null || jsonMessage.isEmpty() || jsonMessage.equals("{}")) {
                log.error("WebSocket消息序列化失败：序列化后的JSON为空");
                return false;
            }

            log.info("WebSocket消息序列化成功，用户ID: {}, messageLength: {}", userId, jsonMessage.length());

            // 发送消息
            boolean sent = UserChannelManager.sendMessage(userId, jsonMessage);

            if (sent) {
                log.info("WebSocket消息发送成功，用户ID: {}", userId);
            } else {
                log.warn("WebSocket消息发送失败，用户可能不在线，用户ID: {}", userId);
            }

            return sent;
        } catch (Exception e) {
            log.error("WebSocket消息序列化或发送失败: type={}", e.getClass().getSimpleName());
            return false;
        }
    }
}
