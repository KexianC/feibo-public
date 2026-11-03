import { authenticatedRequest } from './auth';

const BASE_URL = '/api/v1/admin/model-failure-notices';

export interface ModelFailureNotice {
  id: string;
  requestId: string;
  userId: string | null;
  conversationId: string | null;
  originalModelConfigId: string | null;
  originalProvider: string;
  originalModel: string;
  errorType: string;
  fallbackProvider: string | null;
  fallbackModel: string | null;
  outcome: string;
  createdAt: string;
  read: boolean;
}

export interface ModelFailureNoticePage {
  records: ModelFailureNotice[];
  total: number;
  current: number;
  size: number;
  unreadCount: number;
}

export const modelFailureNoticeService = {
  async list(pageNum = 1, pageSize = 10): Promise<ModelFailureNoticePage> {
    const response = await authenticatedRequest<ModelFailureNoticePage>(
      `${BASE_URL}?pageNum=${pageNum}&pageSize=${pageSize}`,
      { method: 'GET' },
    );
    return response.data;
  },

  async unreadCount(): Promise<number> {
    const response = await authenticatedRequest<number>(`${BASE_URL}/unread-count`, { method: 'GET' });
    return response.data;
  },

  async markRead(id: string): Promise<void> {
    if (typeof id !== 'string' || !/^[1-9]\d*$/.test(id)) {
      throw new Error('通知 ID 无效');
    }
    await authenticatedRequest(`${BASE_URL}/${encodeURIComponent(id)}/read`, { method: 'POST' });
  },

  async markAllRead(): Promise<void> {
    await authenticatedRequest(`${BASE_URL}/read-all`, { method: 'POST' });
  },
};
