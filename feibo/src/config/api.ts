// API配置文件
// 统一管理所有服务器地址和配置

const configuredApiBase = import.meta.env.VITE_API_BASE_URL;
const defaultApiBase = import.meta.env.DEV ? '' : 'https://api.hzmwmt.com';
const apiBaseUrl = (configuredApiBase === undefined ? defaultApiBase : configuredApiBase)
  .trim()
  .replace(/\/+$/, '');
const configuredWebSocketUrl = import.meta.env.VITE_WEBSOCKET_URL;
const webSocketUrl = (configuredWebSocketUrl || 'wss://api.hzmwmt.com/ws').trim();

// 服务器地址配置
export const API_CONFIG = {
  // 开发环境默认使用同源 Vite 代理，生产可由环境变量覆盖。
  BASE_URL: apiBaseUrl,
  // WebSocket服务器地址 wss://api.hzmwmt.com/ws
  WEBSOCKET_URL: webSocketUrl,
  
  // API版本前缀
  API_VERSION: '/api/v1',
  
  // 完整的API基础URL
  get API_BASE_URL() {
    return `${this.BASE_URL}${this.API_VERSION}`;
  },
  
  // WebSocket连接URL
  get WS_URL() {
    return this.WEBSOCKET_URL.replace('http', 'ws');
  }
};

// 请求超时配置
export const REQUEST_CONFIG = {
  TIMEOUT: 10000, // 10秒超时
  RETRY_COUNT: 3, // 重试次数
};

// 认证相关配置
export const AUTH_CONFIG = {
  TOKEN_STORAGE_KEY: 'access_token',
  REFRESH_TOKEN_STORAGE_KEY: 'refresh_token',
  CLIENT_FINGERPRINT_KEY: 'client_fingerprint',
};

// 文件上传类型枚举
export const UPLOAD_TYPE = {
  VIDEO: 0,        // 视频
  RECORDING: 1,    // 录音
  MUSIC: 2,        // 音乐
  BACKGROUND_IMAGE: 3,  // 背景图片
  ILLUSTRATION: 4  // 插图
} as const;

// 文件上传接口配置
export const UPLOAD_CONFIG = {
  // 统一文件上传接口
  UPLOAD_SINGLE_URL: '/digital-human/file/upload-single',
  
  // 获取上传URL（返回相对路径，供authenticatedRequest使用）
  getUploadUrl: (_uploadType: number) => {
    return `${UPLOAD_CONFIG.UPLOAD_SINGLE_URL}`;
  }
};

export default API_CONFIG;
