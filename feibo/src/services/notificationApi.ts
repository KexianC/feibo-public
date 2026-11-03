import { API_CONFIG } from '../config/api';
import { authenticatedRequest } from './authApi';

// 通知消息数据类型
export interface NotificationMessage {
  title: string | null;
  content: string;
  sendTime: string;
}

// 通知消息列表响应类型
export interface NotificationListResponse {
  data: NotificationMessage[];
  hasNext: boolean;
  nextCursorId: number | null;
}

// API响应包装类型
export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

// 查询参数类型
export interface NotificationListParams {
  lastCursorId?: number;
  size?: number;
}

/**
 * 获取通知消息列表
 * @param params 查询参数
 * @returns Promise<NotificationListResponse>
 */
export const getNotificationList = async (params: NotificationListParams = {}): Promise<NotificationListResponse> => {
  try {
    // 构建查询参数
    const queryParams = new URLSearchParams();
    if (params.lastCursorId) {
      queryParams.append('lastCursorId', params.lastCursorId.toString());
    }
    if (params.size) {
      queryParams.append('size', params.size.toString());
    } else {
      queryParams.append('size', '10'); // 默认每页10条
    }
    
    const url = `/message/notify/list?${queryParams.toString()}`;
    
    const response = await authenticatedRequest<any>(url, {
      method: 'GET'
    });
    
    if (response.code !== '0000') {
      throw new Error(response.info || '获取通知消息失败');
    }
    
    const payload = response.data || {};
    const list = Array.isArray(payload.data) ? payload.data : [];
    const hasNext = Boolean(payload.hasNext);
    const nextCursorId = payload.nextCursorId ?? null;
    return { data: list, hasNext, nextCursorId };
  } catch (error) {
    console.error('获取通知消息失败:', error);
    throw error;
  }
};

/**
 * 刷新通知消息列表（获取最新消息）
 * @param size 每页数量
 * @returns Promise<NotificationListResponse>
 */
export const refreshNotificationList = async (size: number = 10): Promise<NotificationListResponse> => {
  return getNotificationList({ size });
};

/**
 * 加载更多通知消息
 * @param lastCursorId 上一页的游标ID
 * @param size 每页数量
 * @returns Promise<NotificationListResponse>
 */
export const loadMoreNotifications = async (lastCursorId: number, size: number = 10): Promise<NotificationListResponse> => {
  return getNotificationList({ lastCursorId, size });
};
