// 用户API服务
// 根据接口文档实现用户资源查询等功能

import { v4 as uuidv4 } from 'uuid';
import { getClientFingerprint, authenticatedRequest } from './authApi';
import { AiFeatureTemplate } from '../types';
import { API_CONFIG } from '../config/api';

// 基础URL配置
const BASE_URL = API_CONFIG.API_BASE_URL;

// 通用响应结构
interface ApiResponse<T = any> {
  code: string;
  info: string;
  data: T;
}

// 用户AI资源
export interface UserAiResource {
  userAiResourceId: number;
  subscriptionBalance: number; // 订阅余额
  purchasedBalance: number; // 购买余额
  aiFeatureTemplate: AiFeatureTemplate;
}

// 用户订阅
export interface UserAiSubscription {
  userAiSubscriptionId: number;
  name: string;
  planCode: string;
  startTime: string;
  endTime: string;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
}

// 用户资源首页响应
export interface UserResourceResponse {
  userResources: UserAiResource[];
  userSubscriptions: UserAiSubscription[];
}

// 使用统一的认证请求函数（已包含401自动刷新Token机制）

// 查询用户AI资源首页
export const fetchUserResources = async (): Promise<UserResourceResponse> => {
  /**
   * 查询用户资源首页
   * 兼容后端返回结构差异：
   * - 后端可能直接返回 userResources 项上的 name/templateCode，而非嵌套的 aiFeatureTemplate
   * - purchasedBalance 可能为 null，需要归一化为 0
   * - userSubscriptions 可能不含 status，基于时间区间计算并补充
   */
  const response = await authenticatedRequest<UserResourceResponse>('/user/resource/index', {
    method: 'GET',
  });

  const raw = response.data as any;

  // 标准化资源列表
  const normalizedResources = (raw?.userResources || []).map((r: any) => {
    const purchased = r?.purchasedBalance == null ? 0 : Number(r.purchasedBalance) || 0;
    const subscription = Number(r?.subscriptionBalance) || 0;
    // 兼容：如果没有 aiFeatureTemplate，则用顶层 name/templateCode 构造
    const aiFeatureTemplate = r?.aiFeatureTemplate ?? {
      name: r?.name ?? '',
      templateCode: r?.templateCode ?? ''
    };
    return {
      // 注意：大整数ID可能被解析为字符串，这里尽量转为数字；若失败则保留原值
      userAiResourceId: typeof r?.userAiResourceId === 'string' ? Number(r.userAiResourceId) : (r?.userAiResourceId ?? 0),
      subscriptionBalance: subscription,
      purchasedBalance: purchased,
      aiFeatureTemplate
    } as UserAiResource;
  });

  // 标准化订阅列表并补充状态
  const now = Date.now();
  const normalizedSubscriptions = (raw?.userSubscriptions || []).map((sub: any) => {
    const planCode = sub?.planCode ?? '';
    const startTime = sub?.startTime ?? '';
    const endTime = sub?.endTime ?? '';
    let status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED' = 'ACTIVE';
    if (planCode === 'PLAN_FREE') {
      status = 'ACTIVE';
    } else {
      const endMs = new Date(endTime).getTime();
      status = !isNaN(endMs) && endMs > now ? 'ACTIVE' : 'EXPIRED';
    }
    return {
      userAiSubscriptionId: typeof sub?.userAiSubscriptionId === 'string' ? Number(sub.userAiSubscriptionId) : (sub?.userAiSubscriptionId ?? 0),
      name: sub?.name ?? '',
      planCode,
      startTime,
      endTime,
      status
    } as UserAiSubscription;
  });

  return {
    userResources: normalizedResources,
    userSubscriptions: normalizedSubscriptions
  };
};

// 获取用户总余额（所有资源的余额总和）
export const getUserTotalBalance = async (): Promise<number> => {
  const resources = await fetchUserResources();
  
  return resources.userResources.reduce((total, resource) => {
    return total + (Number(resource.subscriptionBalance) || 0) + (Number(resource.purchasedBalance) || 0);
  }, 0);
};

// 获取特定类型的资源余额
export const getResourceBalance = async (templateCode: string): Promise<{
  subscriptionBalance: number;
  purchasedBalance: number;
  totalBalance: number;
}> => {
  const resources = await fetchUserResources();
  
  const resource = resources.userResources.find(
    r => r.aiFeatureTemplate.templateCode === templateCode
  );
  
  if (!resource) {
    return {
      subscriptionBalance: 0,
      purchasedBalance: 0,
      totalBalance: 0
    };
  }
  
  return {
    subscriptionBalance: resource.subscriptionBalance,
    purchasedBalance: Number(resource.purchasedBalance) || 0,
    totalBalance: (Number(resource.subscriptionBalance) || 0) + (Number(resource.purchasedBalance) || 0)
  };
};

// 获取活跃的订阅
export const getActiveSubscriptions = async (): Promise<UserAiSubscription[]> => {
  const resources = await fetchUserResources();
  
  return resources.userSubscriptions.filter(sub => sub.status === 'ACTIVE');
};

// 检查订阅是否即将过期（7天内）
export const getExpiringSubscriptions = async (): Promise<UserAiSubscription[]> => {
  const activeSubscriptions = await getActiveSubscriptions();
  const now = new Date();
  const sevenDaysLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
  
  return activeSubscriptions.filter(sub => {
    const endTime = new Date(sub.endTime);
    return endTime <= sevenDaysLater;
  });
};

// 格式化订阅状态
export const formatSubscriptionStatus = (status: string): string => {
  switch (status) {
    case 'ACTIVE':
      return '有效';
    case 'EXPIRED':
      return '已过期';
    case 'CANCELLED':
      return '已取消';
    default:
      return '未知';
  }
};

// 格式化时间
export const formatDateTime = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 计算剩余天数
export const calculateRemainingDays = (endTime: string): number => {
  // 处理特殊字符串情况
  if (endTime === '不足1天' || endTime === '不足一天') {
    return 0; // 返回0表示不足1天
  }
  
  // 处理其他特殊文本
  if (typeof endTime !== 'string' || endTime.trim() === '') {
    return 0;
  }
  
  // 尝试解析日期
  const now = new Date();
  const end = new Date(endTime);
  
  // 检查日期是否有效
  if (isNaN(end.getTime())) {
    return 0;
  }
  
  const diffTime = end.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  return Math.max(0, diffDays);
};

// 格式化余额显示
export const formatBalance = (balance: number): string => {
  if (balance >= 10000) {
    return `${(balance / 10000).toFixed(1)}万`;
  } else if (balance >= 1000) {
    return `${(balance / 1000).toFixed(1)}k`;
  } else {
    return balance.toString();
  }
};

// 获取用户资源摘要
export const getUserResourceSummary = async (): Promise<{
  totalBalance: number;
  activeSubscriptions: number;
  expiringSubscriptions: number;
  resourceTypes: number;
}> => {
  const resources = await fetchUserResources();
  const expiringSubscriptions = await getExpiringSubscriptions();
  
  const totalBalance = resources.userResources.reduce((total, resource) => {
    return total + resource.subscriptionBalance + resource.purchasedBalance;
  }, 0);
  
  const activeSubscriptions = resources.userSubscriptions.filter(
    sub => sub.status === 'ACTIVE'
  ).length;
  
  return {
    totalBalance,
    activeSubscriptions,
    expiringSubscriptions: expiringSubscriptions.length,
    resourceTypes: resources.userResources.length
  };
};

// ===== 子账号管理相关API =====

// 助理/专家用户信息
export interface SubAccountUser {
  userId: number;
  nickName: string;
  avatar: string;
  phoneNumber: string;
  gender: string;
}

// 分页响应结构
export interface PaginatedResponse<T> {
  data: T[];
  hasNext: boolean;
  total: number;
  page: number;
  size: number;
}

// 专家注册助理请求
export interface RegisterAssistantRequest {
  nickName: string;
  phoneNumber: string;
  password: string;
  genderCode: number; // 0-未知, 1-男, 2-女
}

// 专家查询助理列表
export const getExpertAssistants = async (page: number = 1, size: number = 10): Promise<PaginatedResponse<SubAccountUser>> => {
  const response = await authenticatedRequest<PaginatedResponse<SubAccountUser>>(
    `/user/expert/assistants?page=${page}&size=${size}`,
    {
      method: 'GET',
    }
  );
  
  return response.data;
};

// 助理查询专家列表
export const getAssistantExperts = async (page: number = 1, size: number = 10): Promise<PaginatedResponse<SubAccountUser>> => {
  const response = await authenticatedRequest<PaginatedResponse<SubAccountUser>>(
    `/user/assistant/experts?page=${page}&size=${size}`,
    {
      method: 'GET',
    }
  );
  
  return response.data;
};

// 专家注册并绑定新助理
export const registerAssistant = async (assistantData: RegisterAssistantRequest): Promise<void> => {
  await authenticatedRequest(
    '/user/expert/register-assistant',
    {
      method: 'POST',
      body: JSON.stringify(assistantData),
    }
  );
};

// ===== 活动促销相关API =====

// 活动状态查询响应
export interface ActivityStatusResponse {
  isReceived: boolean;
  expireTime: string | null;
}

// 查询用户活动参与状态
export const queryActivityStatus = async (): Promise<ActivityStatusResponse> => {
  const response = await authenticatedRequest<ActivityStatusResponse>('/user/resource/quota/query', {
    method: 'GET',
  });
  
  return response.data;
};

// 领取活动奖励
export const receiveActivityReward = async (): Promise<void> => {
  const uuid = uuidv4();
  await authenticatedRequest('/user/resource/quota/receive', {
    method: 'POST',
  }, 0, uuid);
};

// 计算活动剩余时间（天数）
export const calculateActivityRemainingDays = (expireTime: string | null): number => {
  if (!expireTime) return 0;
  
  // 处理特殊字符串情况
  if (expireTime === '不足1天' || expireTime === '不足一天') {
    return 0; // 返回0表示不足1天
  }
  
  // 处理其他特殊文本
  if (typeof expireTime !== 'string' || expireTime.trim() === '') {
    return 0;
  }
  
  const now = new Date();
  const expire = new Date(expireTime);
  
  // 检查日期是否有效
  if (isNaN(expire.getTime())) {
    return 0;
  }
  
  const diffTime = expire.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  return Math.max(0, diffDays);
};

// 格式化活动剩余时间显示
export const formatActivityRemainingTime = (expireTime: string | null): string => {
  if (!expireTime) return '已过期';
  
  // 直接处理特殊字符串
  if (expireTime === '已过期') {
    return '已过期';
  }
  
  if (expireTime === '不足1天' || expireTime === '不足一天') {
    return '不足1天';
  }
  
  const remainingDays = calculateActivityRemainingDays(expireTime);
  
  if (remainingDays === 0) {
    return '今天到期';
  } else if (remainingDays === 1) {
    return '明天到期';
  } else {
    return `还剩 ${remainingDays} 天`;
  }
};