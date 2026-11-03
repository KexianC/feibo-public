// 认证API服务
// 根据接口文档实现用户注册、登录、刷新token等功能

import { API_CONFIG } from '../config/api';
import { destroyWebSocketService } from './websocketService';
import { v4 as uuidv4 } from 'uuid';
import { requestJson } from './http/client';
import { AppRequestError } from './http/errors';
import { hasAdminRole } from '@/utils/authRole';
import {
  AuthSessionExpiredError,
  authSession,
  isAuthSessionExpiredError,
  isAuthSessionStaleError,
  isSessionExpiredCode,
  type AuthSessionSnapshot,
  type RotatedTokenPair,
} from './authSession';

// 基础URL配置
const BASE_URL = API_CONFIG.API_BASE_URL;

// A WebSocket is authenticated only at handshake time. Tear it down whenever
// the logical browser session is cleared or replaced so a new login cannot
// inherit the previous account's live channel.
authSession.subscribeToResets(destroyWebSocketService);

// 通用响应结构
interface ApiResponse<T = unknown> {
  code: string;
  info: string;
  data: T;
}

// 用户注册请求参数
export interface RegisterRequest {
  nickName: string;
  password: string;
  phoneNumber: string;
  gender: '男' | '女' | '未知';
  clientFingerprint: string;
}

// 用户登录请求参数
export interface LoginRequest {
  phoneNumber: string;
  password: string;
  clientFingerprint: string;
}

// 验证码登录请求参数
export interface SmsLoginRequest {
  phoneNumber: string;
  verificationCode: string;
  clientFingerprint: string;
}

// 发送验证码请求参数
export interface SendSmsRequest {
  phoneNumber: string;
  clientFingerprint: string;
}

// 原密码修改密码请求参数
export interface ChangePasswordByOldPasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// 验证码修改密码请求参数
export interface ChangePasswordBySmsRequest {
  password: string;
  code: string;
}

// 发送修改密码验证码请求参数
export interface SendPasswordResetSmsRequest {
  phoneNumber: string;
  clientFingerprint: string;
}

// 用户角色接口定义
export interface UserRole {
  roleId: number;
  name: string;
  description: string;
}

// 登录响应数据
export interface LoginResponse {
  userId: string;
  nickName: string;
  avatar: string;
  phoneNumber: string;
  gender: string;
  accessToken: string;
  refreshToken: string;
  tokenHead: string;
  roles?: UserRole[]; // 用户角色列表，可选字段
  permissions?: string[];
}

// 生成客户端指纹
export const generateClientFingerprint = (): string => {
  // 基于浏览器特征生成唯一指纹
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  ctx!.textBaseline = 'top';
  ctx!.font = '14px Arial';
  ctx!.fillText('Client fingerprint', 2, 2);
  
  const fingerprint = [
    navigator.userAgent,
    navigator.language,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset(),
    canvas.toDataURL(),
    navigator.hardwareConcurrency || 0,
    (navigator as Navigator & { deviceMemory?: number }).deviceMemory || 0
  ].join('|');
  
  // 生成简单的hash
  let hash = 0;
  for (let i = 0; i < fingerprint.length; i++) {
    const char = fingerprint.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  
  return Math.abs(hash).toString(16);
};

// 获取存储的客户端指纹
export const getClientFingerprint = (): string => {
  let fingerprint = localStorage.getItem('client-fingerprint');
  if (!fingerprint) {
    fingerprint = generateClientFingerprint();
    localStorage.setItem('client-fingerprint', fingerprint);
  }
  return fingerprint;
};

// 通用请求函数
const apiRequest = async <T>(
  url: string,
  options: RequestInit = {},
  uuid?: string
): Promise<ApiResponse<T>> => {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  
  // 如果传入了uuid，添加Idempotency-Token头
  if (uuid) {
    headers.set('Idempotency-Token', uuid);
  }
  
  const { response, data: result } = await requestJson<ApiResponse<T>>(`${BASE_URL}${url}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new AppRequestError(result?.info || `HTTP error! status: ${response.status}`, {
      status: response.status,
      code: result?.code,
    });
  }

  // 检查业务状态码
  if (result.code !== '0000') {
    throw new AppRequestError(result.info || '请求失败', {
      status: response.status,
      code: result.code,
    });
  }

  return result;
};

// 需要认证的请求函数（带401自动刷新Token机制）
// 默认携带幂等令牌，确保请求的幂等性
export const authenticatedRequest = async <T>(
  url: string,
  options: RequestInit = {},
  retryCount = 0,
  uuid?: string
): Promise<ApiResponse<T>> => {
  const requestSession = authSession.capture();
  const accessToken = requestSession.accessToken;
  const clientFingerprint = getClientFingerprint();
  
  if (!accessToken) {
    authSession.expire(requestSession);
    throw new AuthSessionExpiredError();
  }

  // 同一次逻辑请求的刷新重试必须复用幂等令牌。
  const idempotencyToken = uuid || uuidv4();
  const headers = new Headers(options.headers);
  if (options.body instanceof FormData) {
    headers.delete('Content-Type');
  } else if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  // 认证与指纹头最后写入，调用者携带的旧 token 不能覆盖当前会话。
  headers.set('Authorization', `Bearer ${accessToken}`);
  headers.set('client-fingerprint', clientFingerprint);
  headers.set('Idempotency-Token', idempotencyToken);

  const { response, data: result } = await requestJson<ApiResponse<T>>(`${BASE_URL}${url}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    authSession.assertCurrent(requestSession);

    // 其他并发请求可能已经完成刷新，此时直接用新 access token 重试。
    if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
      return authenticatedRequest<T>(url, options, retryCount + 1, idempotencyToken);
    }

    if (retryCount === 0) {
      await recoverUnauthorizedSession(requestSession);
      authSession.assertCurrent(requestSession);
      return authenticatedRequest<T>(url, options, retryCount + 1, idempotencyToken);
    }

    authSession.expire(requestSession);
    throw new AuthSessionExpiredError();
  }

  if (!response.ok) {
    throw new AppRequestError(result?.info || `HTTP error! status: ${response.status}`, {
      status: response.status,
      code: result?.code,
    });
  }

  if (result.code !== '0000') {
    if (isSessionExpiredCode(result.code)) {
      authSession.assertCurrent(requestSession);
      if (localStorage.getItem('access-token') !== accessToken && retryCount === 0) {
        return authenticatedRequest<T>(url, options, retryCount + 1, idempotencyToken);
      }
      authSession.expire(requestSession);
      throw new AuthSessionExpiredError(result.info);
    }

    throw new AppRequestError(result.info || '请求失败', {
      status: response.status,
      code: result.code,
    });
  }

  authSession.assertCurrent(requestSession);
  return result;
};

// 用户注册
export const registerUser = async (data: RegisterRequest): Promise<void> => {
  await apiRequest('/auth/register', {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

export const isAuthSessionChangedError = (error: unknown): boolean =>
  isAuthSessionStaleError(error);

function storeAuthTokens(accessToken: string, refreshToken: string) {
  authSession.replaceTokens(accessToken, refreshToken);
}

// 用户登录（携带幂等令牌确保请求幂等性）
export const loginUser = async (data: LoginRequest): Promise<LoginResponse> => {
  // 生成幂等令牌
  const uuid = uuidv4();
  
  const response = await apiRequest<LoginResponse>('/auth/login/password', {
    method: 'POST',
    body: JSON.stringify(data),
  }, uuid);
  
  // 存储token
  storeAuthTokens(response.data.accessToken, response.data.refreshToken);
  
  return response.data;
};

// 发送短信验证码
export const sendSmsCode = async (data: SendSmsRequest): Promise<void> => {
  const uuid = uuidv4();
  await apiRequest(`/auth/login/send?phoneNumber=${data.phoneNumber}`, {
    method: 'POST',
    body: JSON.stringify(data),
  }, uuid);
};

// 验证码登录（携带幂等令牌确保请求幂等性）
export const smsLogin = async (data: SmsLoginRequest): Promise<LoginResponse> => {
  // 生成幂等令牌
  const uuid = uuidv4();
  
  const response = await apiRequest<LoginResponse>('/auth/login/verification-code', {
    method: 'POST',
    body: JSON.stringify(data),
  }, uuid);
  
  // 存储token
  storeAuthTokens(response.data.accessToken, response.data.refreshToken);
  
  return response.data;
};

// 刷新访问令牌：同一会话的并发刷新共享一个请求。
export const refreshAccessToken = async (
  session: AuthSessionSnapshot = authSession.capture(),
): Promise<string> => {
  try {
    return await authSession.refreshOnce(session, async (refreshToken): Promise<RotatedTokenPair> => {
      const response = await apiRequest<RotatedTokenPair>('/auth/refresh/rotate', {
        method: 'POST',
        cache: 'no-store',
        headers: {
          'Authorization': `Bearer ${refreshToken}`,
          'client-fingerprint': getClientFingerprint(),
        },
      });
      return response.data;
    });
  } catch (error) {
    if (isAuthSessionStaleError(error)) {
      throw error;
    }

    const refreshWasRejected = (
      isAuthSessionExpiredError(error)
      || (
        error instanceof AppRequestError
        && (
          error.status === 401
          || (
            error.status >= 200
            && error.status < 300
            && isSessionExpiredCode(error.code)
          )
        )
      )
    );

    if (refreshWasRejected) {
      if (!authSession.expire(session)) {
        authSession.assertCurrent(session);
      }
      throw new AuthSessionExpiredError();
    }

    // 网络异常、5xx 和 403 不证明会话失效，保留本地会话。
    throw error;
  }
};

// 受保护接口已经返回 401 时，刷新失败意味着当前页面无法继续认证。
// 即使刷新端点暂时返回 403/5xx 或网络错误，也应结束旧会话，避免页面
// 停留在失效状态并持续发起 401 请求。主动刷新仍沿用上面的宽松策略。
export const recoverUnauthorizedSession = async (
  session: AuthSessionSnapshot,
): Promise<string> => {
  try {
    return await refreshAccessToken(session);
  } catch (error) {
    if (isAuthSessionStaleError(error) || isAuthSessionExpiredError(error)) {
      throw error;
    }

    // 并发请求可能已经替换了 access token；优先使用新凭证，不能清除
    // 另一条请求刚恢复完成的同一逻辑会话。
    const currentAccessToken = localStorage.getItem('access-token');
    if (
      currentAccessToken
      && currentAccessToken !== session.accessToken
      && authSession.isCurrent(session)
    ) {
      return currentAccessToken;
    }

    if (!authSession.expire(session)) {
      authSession.assertCurrent(session);
    }
    throw new AuthSessionExpiredError();
  }
};

const notifyBackendLogout = (session: AuthSessionSnapshot): void => {
  if (!session.accessToken || !session.refreshToken) return;

  const headers = new Headers({
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${session.accessToken}`,
    'client-fingerprint': getClientFingerprint(),
    'Idempotency-Token': uuidv4(),
  });

  // 保持 logout() 的同步调用约定，后端注销以 best-effort 方式执行。
  void requestJson<ApiResponse<void>>(`${BASE_URL}/auth/logout`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  }).catch((error) => {
    console.warn('通知服务端注销令牌失败:', error);
  });
};

// 登出
export const logout = (): void => {
  const logoutSession = authSession.capture();
  notifyBackendLogout(logoutSession);
  authSession.clear(logoutSession);

};

// 检查登录状态
export const isLoggedIn = (): boolean => {
  return !!localStorage.getItem('access-token');
};

/**
 * 清理头像URL，去除首尾反引号、引号和多余空白
 * @param avatar 原始头像URL
 * @returns 清理后的可用URL字符串
 */
const sanitizeAvatarUrl = (avatar: string | null | undefined): string => {
  if (!avatar || typeof avatar !== 'string') return '';
  // 去除首尾空白
  let cleaned = avatar.trim();
  // 去除包裹的反引号或引号
  cleaned = cleaned.replace(/^`+|`+$/g, '');
  cleaned = cleaned.replace(/^"+|"+$/g, '');
  cleaned = cleaned.replace(/^'+|'+$/g, '');
  // 简单校验：如果不是http/https开头，则返回原始清理值
  return cleaned;
};

// 获取当前用户信息（兜底清理头像URL）
export const getCurrentUser = (): LoginResponse | null => {
  const userInfo = localStorage.getItem('user-info');
  if (!userInfo) return null;
  try {
    const parsed = JSON.parse(userInfo);
    return {
      ...parsed,
      userId: String(parsed.userId),
      avatar: sanitizeAvatarUrl(parsed.avatar)
    } as LoginResponse;
  } catch {
    return null;
  }
};

// 保存用户信息（保存前清理头像URL）
export const saveUserInfo = (userInfo: LoginResponse): void => {
  // 确保userId为字符串格式，并清理头像URL
  const userInfoToSave = {
    userId: String(userInfo.userId),
    nickName: userInfo.nickName,
    avatar: sanitizeAvatarUrl(userInfo.avatar),
    phoneNumber: userInfo.phoneNumber,
    gender: userInfo.gender,
    roles: userInfo.roles,
  };
  
  const jsonString = JSON.stringify(userInfoToSave);
  localStorage.setItem('user-info', jsonString);
};

// 发送修改密码验证码
export const sendPasswordResetSms = async (data: SendPasswordResetSmsRequest): Promise<void> => {
  const phone = encodeURIComponent(data.phoneNumber);
  await authenticatedRequest('/user/update/send?phoneNumber=' + phone, {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

// 通过原密码修改密码
export const changePasswordByOldPassword = async (data: ChangePasswordByOldPasswordRequest): Promise<void> => {
  await authenticatedRequest('/auth/change-password-by-old', {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

// 通过验证码修改密码
export const changePasswordBySms = async (data: ChangePasswordBySmsRequest): Promise<void> => {
  await authenticatedRequest('/user/update/password', {
    method: 'POST',
    body: JSON.stringify(data),
  });
};

// 用户信息接口
export interface UserInfo {
  userId: string;
  username: string;
  nickName: string;
  phoneNumber: string;
  avatar: string;
  gender: string;
  permissions?: string[];
}

// 新接口返回的数据结构（仅包含被修改后的用户基础信息）
export interface UpdateUserData {
  userId: number;
  nickName: string;
  avatar: string;
  gender: number;
  phoneNumber: string;
}

// 更新用户名请求参数
export interface UpdateUsernameRequest {
  username: string;
}

// 更新手机号请求参数
export interface UpdatePhoneRequest {
  phoneNumber: string;
  password: string;
}

// 获取用户信息
export const getUserInfo = async (): Promise<UserInfo> => {
  const response = await authenticatedRequest<UserInfo>('/user/info', {
    method: 'GET',
  });
  return response.data;
};

/**
 * 上传头像并返回服务端更新后的用户信息
 * - 兼容服务端将头像URL用反引号/引号包裹的情况
 * - 在返回前自动清理 avatar 字段
 */
export const uploadAvatar = async (file: File): Promise<UpdateUserData> => {
  const formData = new FormData();
  formData.append('avatar', file);
  
  const response = await authenticatedRequest<UpdateUserData>('/user/update/avatar', {
    method: 'POST',
    body: formData,
  });
  const data = response.data;
  // 清理头像URL中的反引号/引号与空白
  const cleanedAvatar = sanitizeAvatarUrl(data?.avatar);
  return {
    ...data,
    avatar: cleanedAvatar,
  };
};

/**
 * 更新用户名并返回服务端更新后的用户信息
 * - 返回的 data 中包含 nickName、avatar 等信息
 * - 在返回前自动清理 avatar 字段
 */
export const updateUsername = async (data: UpdateUsernameRequest): Promise<UpdateUserData> => {
  const response = await authenticatedRequest<UpdateUserData>(`/user/update/nick-name?nickName=${encodeURIComponent(data.username)}`, {
    method: 'POST',
  });
  const res = response.data;
  return {
    ...res,
    avatar: sanitizeAvatarUrl(res?.avatar),
  };
};

// 更新手机号（需要密码验证）
export const updatePhoneNumber = async (data: UpdatePhoneRequest): Promise<void> => {
  await authenticatedRequest('/user/phone', {
    method: 'PUT',
    body: JSON.stringify(data),
  });
};

/**
 * 判断当前用户是否为管理员
 * 通过检查用户角色列表中是否包含ADMIN角色来判断
 * @returns {boolean} 如果用户是管理员返回true，否则返回false
 */
export const isAdmin = (): boolean => {
  return hasAdminRole(getCurrentUser());
};

// 自动刷新token的拦截器
export const setupTokenRefreshInterceptor = () => {
  // 可以在这里设置定时器或者响应拦截器来自动刷新token
  const intervalId = window.setInterval(async () => {
    if (isLoggedIn()) {
      try {
        await refreshAccessToken();
      } catch (error) {
        if (isAuthSessionStaleError(error) || isAuthSessionExpiredError(error)) {
          return;
        }
        console.error('自动刷新token失败:', error);
        // 短暂的网络或服务端异常不清除仍可恢复的会话。
      }
    }
  }, 10 * 60 * 1000); // 访问令牌默认15分钟，提前刷新以避免活跃会话中断

  return () => window.clearInterval(intervalId);
};
