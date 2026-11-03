// WebSocket连接状态枚举
export enum WebSocketState {
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  RECONNECTING = 'reconnecting',
  ERROR = 'error'
}

// WebSocket消息类型
export interface WebSocketMessage {
  type: string;
  data?: any;
  timestamp?: number;
  [key: string]: any; // 允许其他属性如userId等
}

// 消息订阅回调函数类型
export type MessageCallback = (message: WebSocketMessage) => void;

// WebSocket配置接口
export interface WebSocketConfig {
  url: string;
  heartbeatInterval?: number; // 心跳间隔，默认30秒
  reconnectInterval?: number; // 重连间隔，默认5秒
  maxReconnectAttempts?: number; // 最大重连次数，默认5次
  debug?: boolean; // 是否开启调试日志
}

interface WebSocketTicket {
  ticket: string;
  protocol: string;
  expiresInSeconds: number;
}

/**
 * WebSocket服务类
 * 提供连接管理、消息收发、心跳保持、自动重连等功能
 */
export class WebSocketService {
  private ws: WebSocket | null = null;
  private config: Required<WebSocketConfig>;
  private state: WebSocketState = WebSocketState.DISCONNECTED;
  private reconnectAttempts = 0;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private connectPromise: Promise<void> | null = null;
  private messageSubscribers: Map<string, Set<MessageCallback>> = new Map();
  private stateChangeCallbacks: Set<(state: WebSocketState) => void> = new Set();

  constructor(config: WebSocketConfig) {
    this.config = {
      heartbeatInterval: 30000, // 30秒
      reconnectInterval: 5000,  // 5秒
      maxReconnectAttempts: 5,
      debug: false,
      ...config
    };

    // 监听页面关闭事件，确保正确断开连接
    window.addEventListener('beforeunload', () => {
      this.disconnect();
    });
  }

  /**
   * 建立WebSocket连接
   */
  public connect(): Promise<void> {
    if (this.state === WebSocketState.CONNECTED) {
      return Promise.resolve();
    }
    if (this.connectPromise) {
      return this.connectPromise;
    }
    this.connectPromise = this.openAuthenticatedSocket()
      .finally(() => {
        this.connectPromise = null;
      });
    return this.connectPromise;
  }

  private async openAuthenticatedSocket(): Promise<void> {
    this.setState(WebSocketState.CONNECTING);
    this.log('申请一次性 WebSocket ticket');

    try {
      // Dynamic import avoids the authApi -> websocketService logout dependency
      // becoming a static circular import.
      const { authenticatedRequest } = await import('./authApi');
      const response = await authenticatedRequest<WebSocketTicket>('/ws/ticket', {
        method: 'POST',
      });
      const ticket = response.data;
      if (!ticket?.ticket || !ticket.protocol) {
        throw new Error('WebSocket ticket 响应无效');
      }

      await new Promise<void>((resolve, reject) => {
        this.log('开始建立已认证 WebSocket');
        this.ws = new WebSocket(this.config.url, [
          ticket.protocol,
          `ticket.${ticket.ticket}`,
        ]);

        this.ws.onopen = () => {
          this.log('WebSocket连接成功');
          this.setState(WebSocketState.CONNECTED);
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          resolve();
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(event);
        };

        this.ws.onclose = (event) => {
          this.log(`WebSocket连接关闭，状态码: ${event.code}`);
          this.handleClose(event);
        };

        this.ws.onerror = (error) => {
          this.log('WebSocket连接错误');
          this.setState(WebSocketState.ERROR);
          reject(error);
        };

      });
    } catch (error) {
      this.log('创建已认证 WebSocket 连接失败');
      this.setState(WebSocketState.ERROR);
      throw error;
    }
  }

  /**
   * 断开WebSocket连接
   */
  public disconnect(): void {
    this.log('主动断开WebSocket连接');
    
    // 停止心跳和重连
    this.stopHeartbeat();
    this.stopReconnect();
    
    // 关闭连接
    if (this.ws) {
      this.ws.close(1000, '主动断开');
      this.ws = null;
    }
    
    this.setState(WebSocketState.DISCONNECTED);
  }

  /**
   * 发送消息
   */
  public send(message: WebSocketMessage): boolean {
    if (this.state !== WebSocketState.CONNECTED || !this.ws) {
      this.log('WebSocket未连接，无法发送消息');
      return false;
    }

    try {
      const messageWithTimestamp = {
        ...message,
        timestamp: Date.now()
      };
      
      this.ws.send(JSON.stringify(messageWithTimestamp));
      this.log(`发送消息，类型: ${messageWithTimestamp.type}`);
      return true;
    } catch (error) {
      this.log('发送消息失败');
      return false;
    }
  }

  /**
   * 发送二进制帧。实时 ASR 后端只接受原始 PCM WebSocket 二进制数据，
   * 不能经过 JSON 或 Base64 包装。
   */
  public sendBinary(data: ArrayBuffer | ArrayBufferView | Blob): boolean {
    if (this.state !== WebSocketState.CONNECTED || !this.ws) {
      this.log('WebSocket未连接，无法发送二进制消息');
      return false;
    }

    try {
      this.ws.send(data);
      this.log('发送二进制消息');
      return true;
    } catch {
      this.log('发送二进制消息失败');
      return false;
    }
  }

  /**
   * 订阅特定类型的消息
   */
  public subscribe(messageType: string, callback: MessageCallback): () => void {
    if (!this.messageSubscribers.has(messageType)) {
      this.messageSubscribers.set(messageType, new Set());
    }
    
    this.messageSubscribers.get(messageType)!.add(callback);
    this.log(`订阅消息类型: ${messageType}`);
    
    // 返回取消订阅函数
    return () => {
      this.unsubscribe(messageType, callback);
    };
  }

  /**
   * 取消订阅
   */
  public unsubscribe(messageType: string, callback: MessageCallback): void {
    const subscribers = this.messageSubscribers.get(messageType);
    if (subscribers) {
      subscribers.delete(callback);
      if (subscribers.size === 0) {
        this.messageSubscribers.delete(messageType);
      }
      this.log(`取消订阅消息类型: ${messageType}`);
    }
  }

  /**
   * 订阅连接状态变化
   */
  public onStateChange(callback: (state: WebSocketState) => void): () => void {
    this.stateChangeCallbacks.add(callback);
    
    // 返回取消订阅函数
    return () => {
      this.stateChangeCallbacks.delete(callback);
    };
  }

  /**
   * 获取当前连接状态
   */
  public getState(): WebSocketState {
    return this.state;
  }

  /**
   * 检查是否已连接
   */
  public isConnected(): boolean {
    return this.state === WebSocketState.CONNECTED;
  }

  /**
   * 处理接收到的消息
   */
  private handleMessage(event: MessageEvent): void {
    try {
      const message: WebSocketMessage = JSON.parse(event.data);
      this.log(`收到消息，类型: ${message.type}`);

      // 处理心跳响应
      if (message.type === 'pong') {
        this.log('收到心跳响应');
        return;
      }

      // 检查是否为支付成功消息
      if (message.title === '交易通知' && message.content && message.content.includes('支付成功')) {
        this.log('检测到支付成功消息');
        // 发送支付成功事件
        const paymentSuccessMessage: WebSocketMessage = {
          type: 'payment_success',
          data: {
            title: message.title,
            content: message.content,
            createTime: message.createTime
          },
          timestamp: Date.now()
        };
        
        // 分发支付成功消息给订阅者
        const paymentSubscribers = this.messageSubscribers.get('payment_success');
        if (paymentSubscribers) {
          paymentSubscribers.forEach(callback => {
            try {
              callback(paymentSuccessMessage);
            } catch (error) {
              this.log('支付成功消息回调执行错误');
            }
          });
        }
      }

      // 分发消息给订阅者
      const subscribers = this.messageSubscribers.get(message.type);
      if (subscribers) {
        subscribers.forEach(callback => {
          try {
            callback(message);
          } catch (error) {
            this.log('消息回调执行错误');
          }
        });
      }

      // 分发给通用订阅者
      const allSubscribers = this.messageSubscribers.get('*');
      if (allSubscribers) {
        allSubscribers.forEach(callback => {
          try {
            callback(message);
          } catch (error) {
            this.log('通用消息回调执行错误');
          }
        });
      }

    } catch (error) {
      this.log('解析消息失败');
    }
  }

  /**
   * 处理连接关闭
   */
  private handleClose(event: CloseEvent): void {
    this.stopHeartbeat();
    this.ws = null;

    // 如果是正常关闭或主动断开，不进行重连
    if (event.code === 1000 || this.state === WebSocketState.DISCONNECTED) {
      this.setState(WebSocketState.DISCONNECTED);
      return;
    }

    // 异常关闭，尝试重连
    this.setState(WebSocketState.RECONNECTING);
    this.attemptReconnect();
  }

  /**
   * 尝试重连
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      this.log(`重连次数已达上限(${this.config.maxReconnectAttempts})，停止重连`);
      this.setState(WebSocketState.ERROR);
      return;
    }

    this.reconnectAttempts++;
    this.log(`第${this.reconnectAttempts}次重连尝试，${this.config.reconnectInterval}ms后开始`);

    this.reconnectTimer = setTimeout(() => {
      if (this.state === WebSocketState.RECONNECTING) {
        this.connect().catch(error => {
          this.log('重连失败');
          this.attemptReconnect();
        });
      }
    }, this.config.reconnectInterval);
  }

  /**
   * 停止重连
   */
  private stopReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * 开始心跳
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();
    
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected()) {
        this.send({ type: 'ping', data: null });
      }
    }, this.config.heartbeatInterval);
    
    this.log('心跳已启动，间隔:', this.config.heartbeatInterval + 'ms');
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
      this.log('心跳已停止');
    }
  }

  /**
   * 设置连接状态
   */
  private setState(newState: WebSocketState): void {
    if (this.state !== newState) {
      const oldState = this.state;
      this.state = newState;
      this.log(`状态变化: ${oldState} -> ${newState}`);
      
      // 通知状态变化订阅者
      this.stateChangeCallbacks.forEach(callback => {
        try {
          callback(newState);
        } catch (error) {
          this.log('状态变化回调执行错误');
        }
      });
    }
  }

  /**
   * 调试日志
   */
  private log(message: string, ..._sensitiveMetadata: unknown[]): void {
    if (this.config.debug) {
      console.log('[WebSocket]', message);
    }
  }

  /**
   * 销毁服务
   */
  public destroy(): void {
    this.disconnect();
    this.messageSubscribers.clear();
    this.stateChangeCallbacks.clear();
  }
}

// 创建全局WebSocket服务实例
let globalWebSocketService: WebSocketService | null = null;

/**
 * 获取全局WebSocket服务实例
 */
export const getWebSocketService = (config?: WebSocketConfig): WebSocketService => {
  if (!globalWebSocketService && config) {
    globalWebSocketService = new WebSocketService(config);
  }
  
  if (!globalWebSocketService) {
    throw new Error('WebSocket服务未初始化，请先提供配置');
  }
  
  return globalWebSocketService;
};

/**
 * 销毁全局WebSocket服务实例
 */
export const destroyWebSocketService = (): void => {
  if (globalWebSocketService) {
    globalWebSocketService.destroy();
    globalWebSocketService = null;
  }
};
