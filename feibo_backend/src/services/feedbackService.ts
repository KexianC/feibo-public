import { authenticatedRequest } from './auth';

/**
 * 枚举：反馈类型
 */
export enum FeedbackType {
  FUNCTION_SUGGESTION = 1,
  BUG_REPORT = 2,
  COMPLAINT = 3,
  OTHER = 4,
}

/**
 * 枚举：反馈状态
 */
export enum FeedbackStatus {
  PENDING = 0,
  PROCESSING = 1,
  RESOLVED = 2,
  CLOSED = 3,
}

/**
 * 枚举：反馈优先级
 */
export enum FeedbackPriority {
  LOW = 0,
  MEDIUM = 1,
  HIGH = 2,
  URGENT = 3,
}

/**
 * 用户反馈数据结构（页面展示用）
 */
/**
 * 原始反馈数据结构（与后端返回保持一致）
 */
export interface RawFeedback {
  id: number;
  feedbackId?: number;
  userId: string | number;
  type: number;
  title?: string;
  content?: string;
  createTime: string;
  updateTime?: string;
  username?: string;
  rating?: number;
  status?: FeedbackStatus;
  priority?: FeedbackPriority;
  reply?: string;
  version: number;
}

/**
 * 页面展示用的反馈数据结构（统一为字符串ID等）
 */
export interface UserFeedback {
  id: string;
  feedbackId?: string;
  userId: string;
  type: number; // 参照 FeedbackType
  title?: string;
  content: string; // 若后端缺失，使用标题或空字符串回退
  createTime: string;
  updateTime?: string;
  username?: string;
  rating?: number;
  status?: FeedbackStatus;
  priority?: FeedbackPriority;
  reply?: string;
  version: number;
}

/**
 * 分页查询参数（对齐后台文档）
 */
export interface FeedbackPageParams {
  pageNum: number;
  pageSize: number;
  userId?: string;
  status?: FeedbackStatus;
  priority?: FeedbackPriority;
  startDate?: string;
  endDate?: string;
  type?: number;
  keyword?: string;
}

/**
 * 通用分页响应
 */
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

/**
 * 反馈服务：分页查询、回复、状态变更
 */
export class FeedbackService {
  private static instance: FeedbackService;

  private constructor() {}

  /** 获取单例实例 */
  public static getInstance(): FeedbackService {
    if (!FeedbackService.instance) {
      FeedbackService.instance = new FeedbackService();
    }
    return FeedbackService.instance;
  }

  /**
   * 分页查询用户反馈列表
   * GET /api/admin/user/feedback/page
   * 支持 userId、type、keyword 筛选
   */
  async getFeedbackPage(params: FeedbackPageParams): Promise<PageResponse<UserFeedback>> {
    const qs = new URLSearchParams();
    qs.append('pageNum', String(params.pageNum));
    qs.append('pageSize', String(params.pageSize));
    if (params.userId !== undefined) qs.append('userId', String(params.userId));
    if (params.type !== undefined) qs.append('type', String(params.type));
    if (params.keyword) qs.append('keyword', params.keyword);
    if (params.status !== undefined) qs.append('status', String(params.status));
    if (params.priority !== undefined) qs.append('priority', String(params.priority));
    if (params.startDate) qs.append('startDate', params.startDate);
    if (params.endDate) qs.append('endDate', params.endDate);

    const resp = await authenticatedRequest<{ records: RawFeedback[]; total: number; size: number; current: number; pages: number }>(
      `/api/admin/user/feedback/page?${qs.toString()}`,
      { method: 'GET' }
    );
    const data = resp.data;
    // 映射结构，确保字段类型一致
    const normalized: PageResponse<UserFeedback> = {
      records: (data?.records || []).map((it: RawFeedback): UserFeedback => {
        const idStr = String(it.id);
        const feedbackIdStr = it.feedbackId !== undefined ? String(it.feedbackId) : undefined;
        const userIdStr = String(it.userId ?? '');
        const typeNum = Number(it.type ?? 0);
        // 内容字段回退：优先使用 content，其次 title，最后空字符串
        const contentText = (it.content ?? it.title ?? '').toString();
        return {
          id: idStr,
          feedbackId: feedbackIdStr,
          userId: userIdStr,
          type: typeNum,
          title: it.title,
          content: contentText,
          createTime: it.createTime,
          updateTime: it.updateTime,
          username: it.username,
          rating: it.rating,
          status: it.status,
          priority: it.priority,
          reply: it.reply,
          version: Number(it.version ?? 0),
        };
      }),
      total: data.total || 0,
      pages: data.pages || 0,
      size: data.size || params.pageSize,
      current: data.current || params.pageNum,
    };
    return normalized;
  }

  /**
   * 回复反馈
   * POST /api/admin/user/feedback/{id}/reply
   */
  private async process(id: string, action: 'reply' | 'resolve' | 'close', input: { version: number; reply?: string; priority?: number }): Promise<void> {
    const response = await authenticatedRequest<UserFeedback>(`/api/admin/user/feedback/${encodeURIComponent(id)}/${action}`, {
      method: 'POST', body: JSON.stringify(input),
    });
    if (!response.data?.id || Number(response.data.version) <= input.version) throw new Error('后端未确认反馈处理结果，请刷新核对');
  }
  replyFeedback(id: string, reply: string, version: number, priority?: number): Promise<void> {
    return this.process(id, 'reply', { reply, version, priority });
  }
  resolveFeedback(id: string, version: number): Promise<void> { return this.process(id, 'resolve', { version }); }
  closeFeedback(id: string, version: number): Promise<void> { return this.process(id, 'close', { version }); }

  /**
   * 类型配置映射（颜色与文案）
   */
  getTypeConfig(type: number): { color: string; text: string } {
    const map: Record<number, { color: string; text: string }> = {
      0: { color: 'green', text: '其他' },
      [FeedbackType.FUNCTION_SUGGESTION]: { color: 'blue', text: '功能建议' },
      [FeedbackType.BUG_REPORT]: { color: 'red', text: 'Bug反馈' },
      [FeedbackType.COMPLAINT]: { color: 'orange', text: '投诉' },
      [FeedbackType.OTHER]: { color: 'green', text: '其他' },
    };
    return map[type] || { color: 'default', text: String(type) };
  }

  /**
   * 状态配置映射（颜色与文案）
   */
  getStatusConfig(status?: number): { color: string; text: string } {
    const map: Record<number, { color: string; text: string }> = {
      [FeedbackStatus.PENDING]: { color: 'default', text: '待处理' },
      [FeedbackStatus.PROCESSING]: { color: 'blue', text: '处理中' },
      [FeedbackStatus.RESOLVED]: { color: 'green', text: '已解决' },
      [FeedbackStatus.CLOSED]: { color: 'gray', text: '已关闭' },
    };
    if (status === undefined || status === null) return { color: 'default', text: '-' };
    return map[status] || { color: 'default', text: String(status) };
  }

  /**
   * 优先级配置映射（颜色与文案）
   */
  getPriorityConfig(priority?: number): { color: string; text: string } {
    const map: Record<number, { color: string; text: string }> = {
      [FeedbackPriority.LOW]: { color: 'default', text: '低' },
      [FeedbackPriority.MEDIUM]: { color: 'blue', text: '中' },
      [FeedbackPriority.HIGH]: { color: 'orange', text: '高' },
      [FeedbackPriority.URGENT]: { color: 'red', text: '紧急' },
    };
    if (priority === undefined || priority === null) return { color: 'default', text: '-' };
    return map[priority] || { color: 'default', text: String(priority) };
  }
}

export default FeedbackService.getInstance();