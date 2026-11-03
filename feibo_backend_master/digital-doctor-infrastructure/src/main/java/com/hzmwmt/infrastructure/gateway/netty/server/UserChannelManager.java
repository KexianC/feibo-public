package com.hzmwmt.infrastructure.gateway.netty.server;

import com.hzmwmt.types.enums.ResponseCode;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserChannelManager
 * @Date: 2025/8/29 17:23
 */
@Slf4j
public class UserChannelManager {
    /**
     * 使用 ConcurrentHashMap 来存储用户ID和Channel的映射关系，保证线程安全。
     */
    private static final ConcurrentHashMap<Long, Channel> USER_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * 绑定用户ID和Channel。在用户连接并认证成功后调用。
     *
     * @param userId  用户唯一标识
     * @param channel 用户的Netty Channel
     */
    public static void bind(Long userId, Channel channel) {
        if (userId == null || userId <= 0 || channel == null) {
            throw new IllegalArgumentException("Authenticated user and channel are required");
        }
        Channel previous = USER_CHANNEL_MAP.put(userId, channel);
        if (previous != null && previous != channel) {
            log.warn("用户 [{}] 的旧连接 [{}] 被新连接 [{}] 替换。",
                    userId, previous.id().asShortText(), channel.id().asShortText());
            previous.close();
        }
        log.info("用户 [{}] 绑定成功，Channel ID: {}", userId, channel.id().asShortText());
    }

    /**
     * 根据Channel来解绑用户。在用户断开连接时调用。
     *
     * @param channel 待解绑的Netty Channel
     */
    public static void unbind(Channel channel) {
        // 遍历Map，找到对应的Entry并移除
        USER_CHANNEL_MAP.entrySet().stream()
                .filter(entry -> entry.getValue() == channel)
                .findFirst()
                .ifPresent(entry -> {
                    Long userId = entry.getKey();
                    if (USER_CHANNEL_MAP.remove(userId, channel)) {
                        log.info("用户 [{}] 解绑成功，Channel ID: {}", userId, channel.id().asShortText());
                    }
                });
    }

    /**
     * 根据用户ID获取其Channel。
     *
     * @param userId 用户ID
     * @return 用户的Netty Channel，如果用户不在线则返回null
     */
    public static Channel getChannel(Long userId) {
        return USER_CHANNEL_MAP.get(userId);
    }

    /**
     * 向指定用户发送消息（单点推送）。
     *
     * @param userId  目标用户的ID
     * @param message 要发送的消息文本
     */
    public static Boolean sendMessage(Long userId, String message) {
        // 检查参数是否有效
        if (userId == null) {
            log.error("WebSocket消息发送失败：用户ID为空");
            return false;
        }

        if (message == null || message.isEmpty() || message.equals("{}")) {
            log.error("WebSocket消息发送失败：消息内容为空，用户ID: {}", userId);
            return false;
        }

        // 获取用户通道
        Channel channel = getChannel(userId);

        // 检查通道状态
        if (channel == null) {
            log.warn("向用户 [{}] 推送消息失败，未找到用户通道", userId);
            return false;
        }

        if (!channel.isActive()) {
            log.warn("向用户 [{}] 推送消息失败，通道不活跃，通道ID: {}", userId, channel.id().asShortText());
            // 清理不活跃的通道
            unbind(channel);
            return false;
        }

        if (!channel.isWritable()) {
            log.warn("向用户 [{}] 推送消息失败，通道不可写，通道ID: {}", userId, channel.id().asShortText());
            return false;
        }

        try {
            // 创建WebSocket消息帧
            TextWebSocketFrame frame = new TextWebSocketFrame(message);

            // 发送消息
            channel.writeAndFlush(frame);

            log.info("向用户 [{}] 推送消息成功，通道ID: {}, 消息长度: {}",
                    userId, channel.id().asShortText(), message.length());

            return true;
        } catch (Exception e) {
            log.error("向用户推送消息异常: userId={}, code={}, type={}",
                    userId, ResponseCode.SERVICE_ERROR.getCode(), exceptionType(e));

            // 发生异常时清理通道
            try {
                unbind(channel);
            } catch (Exception unbindEx) {
                log.error("清理异常通道失败: code={}, type={}",
                        ResponseCode.SERVICE_ERROR.getCode(), exceptionType(unbindEx));
            }

            return false;
        }
    }

    /**
     * 向所有在线用户发送消息（广播）。
     *
     * @param message 要广播的消息文本
     */
    public static void sendToAll(String message) {
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        USER_CHANNEL_MAP.values().forEach(channel -> {
            if (channel != null && channel.isActive()) {
                // 为了性能，可以一次创建Frame，多次发送
                channel.writeAndFlush(frame.retainedDuplicate());
            }
        });
        log.info("广播消息成功，消息长度: {}", message == null ? 0 : message.length());
    }

    /**
     * 获取当前在线用户数量。
     *
     * @return 在线用户数
     */
    public static int getOnlineCount() {
        return USER_CHANNEL_MAP.size();
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }
}

