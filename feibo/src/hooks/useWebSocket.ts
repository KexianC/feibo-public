import { useEffect, useRef, useCallback, useState } from 'react';
import { getWebSocketService, WebSocketState, WebSocketMessage, MessageCallback } from '../services/websocketService';

/**
 * WebSocket Hook
 * 提供WebSocket连接状态管理和消息订阅功能
 */
export const useWebSocket = () => {
  const wsServiceRef = useRef<ReturnType<typeof getWebSocketService> | null>(null);

  // 获取WebSocket服务实例
  const getService = useCallback(() => {
    if (!wsServiceRef.current) {
      try {
        wsServiceRef.current = getWebSocketService();
      } catch (error) {
        console.warn('WebSocket服务未初始化');
        return null;
      }
    }
    return wsServiceRef.current;
  }, []);

  // 发送消息
  const sendMessage = useCallback((message: WebSocketMessage): boolean => {
    const service = getService();
    if (service) {
      return service.send(message);
    }
    return false;
  }, [getService]);

  const sendBinary = useCallback((data: ArrayBuffer | ArrayBufferView | Blob): boolean => {
    const service = getService();
    return service ? service.sendBinary(data) : false;
  }, [getService]);

  // 订阅消息
  const subscribe = useCallback((messageType: string, callback: MessageCallback) => {
    const service = getService();
    if (service) {
      return service.subscribe(messageType, callback);
    }
    return () => {}; // 返回空的取消订阅函数
  }, [getService]);

  // 订阅连接状态变化
  const onStateChange = useCallback((callback: (state: WebSocketState) => void) => {
    const service = getService();
    if (service) {
      return service.onStateChange(callback);
    }
    return () => {}; // 返回空的取消订阅函数
  }, [getService]);

  // 获取连接状态
  const getConnectionState = useCallback((): WebSocketState | null => {
    const service = getService();
    return service ? service.getState() : null;
  }, [getService]);

  // 检查是否已连接
  const isConnected = useCallback((): boolean => {
    const service = getService();
    return service ? service.isConnected() : false;
  }, [getService]);

  // 手动连接
  const connect = useCallback(async (): Promise<boolean> => {
    const service = getService();
    if (service) {
      try {
        await service.connect();
        return true;
      } catch (error) {
        console.error('WebSocket连接失败:', error);
        return false;
      }
    }
    return false;
  }, [getService]);

  // 手动断开连接
  const disconnect = useCallback((): void => {
    const service = getService();
    if (service) {
      service.disconnect();
    }
  }, [getService]);

  return {
    sendMessage,
    sendBinary,
    subscribe,
    onStateChange,
    getConnectionState,
    isConnected,
    connect,
    disconnect
  };
};

/**
 * 消息订阅Hook
 * 自动管理消息订阅的生命周期
 */
export const useWebSocketMessage = (
  messageType: string,
  callback: MessageCallback,
  deps: React.DependencyList = []
) => {
  const { subscribe } = useWebSocket();

  useEffect(() => {
    const unsubscribe = subscribe(messageType, callback);
    return unsubscribe;
  }, [messageType, subscribe, ...deps]);
};

/**
 * 连接状态Hook
 * 监听WebSocket连接状态变化
 */
export const useWebSocketState = () => {
  const { onStateChange, getConnectionState } = useWebSocket();
  const [state, setState] = useState<WebSocketState | null>(getConnectionState());

  useEffect(() => {
    const unsubscribe = onStateChange((newState) => {
      setState(newState);
    });
    return unsubscribe;
  }, [onStateChange]);

  return state;
};

export default useWebSocket;
