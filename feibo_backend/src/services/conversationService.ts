import { authenticatedRequest } from './auth';

// 对话信息接口 - 匹配API响应结构
export interface Conversation {
  id: string; // 大整数ID，需要作为字符串处理避免精度丢失
  username?: string;
  title: string; // 对话标题/问题内容
  model?: string;
  createTime: string;
  tokenNumber?: number; // API返回的字段名
  // 为了兼容显示，添加计算属性
  tokens?: number; // 映射自tokenNumber
  measuredTokens?: number;
  estimatedCost?: number | string | null;
  meteredReplies?: number;
  pricedReplies?: number;
  totalReplies?: number;
  question?: string; // 映射自title，用于显示
}

// 对话详情接口
export interface ConversationDetail {
  id: string;
  conversationId: string;
  userId: string;
  username?: string;
  title: string;
  messages: ConversationMessage[];
  tokens?: number;
  model?: string;
  status?: 'success' | 'failed' | 'pending';
  createTime: string;
  updateTime?: string;
  estimatedCost?: number | string | null;
  meteredReplies?: number;
  pricedReplies?: number;
  totalReplies?: number;
}

// 对话消息接口
export interface ConversationMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createTime: string;
  model?: string;
  modelId?: string;
  legacyEstimatedTokens?: number;
  usage?: ChatUsageRecord;
}

export interface ChatUsageRecord {
  provider?: string;
  model?: string;
  responseModel?: string;
  requestedAt?: string;
  usageSource?: string;
  inputTokens?: number | null;
  outputTokens?: number | null;
  cachedTokens?: number | null;
  reasoningTokens?: number | null;
  totalTokens?: number | null;
  costStatus?: string;
  estimatedCost?: number | string | null;
  currency?: string;
  inputPrice?: number | string;
  outputPrice?: number | string;
  cachePrice?: number | string;
  priceVersion?: string;
  priceSource?: string;
  priceNote?: string;
}

// 分页查询参数
// 对话分页查询参数（与后端文档保持一致）
export interface ConversationPageParams {
  pageNum: number; // 页码，默认第1页
  pageSize: number; // 每页条数，默认10条
  username?: string; // 用户名（模糊匹配）
  content?: string; // 问题内容（模糊匹配）
  status?: string; // 对话状态
  model?: string; // int64 IDs must retain all digits in JavaScript.
  startDate?: string;
  endDate?: string;
}

// 分页响应
export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

export class ConversationService {
  private static instance: ConversationService;

  private constructor() {
    // 私有构造函数，确保单例模式
  }

  public static getInstance(): ConversationService {
    if (!ConversationService.instance) {
      ConversationService.instance = new ConversationService();
    }
    return ConversationService.instance;
  }

  /**
   * 分页查询对话列表
   */
  /**
   * 分页查询对话列表（参数名对齐：pageNum/pageSize, username, content, status, model）
   */
  async getConversationsPage(params: ConversationPageParams): Promise<PageResponse<Conversation>> {
    const queryParams = new URLSearchParams();
    queryParams.append('pageNum', params.pageNum.toString());
    queryParams.append('pageSize', params.pageSize.toString());
    
    if (params.username) {
      queryParams.append('username', params.username);
    }
    if (params.content) {
      queryParams.append('content', params.content);
    }
    if (params.status) {
      queryParams.append('status', params.status);
    }
    if (params.model && /^[1-9][0-9]{0,18}$/.test(params.model)) {
      queryParams.append('model', String(params.model));
    }
    if (params.startDate) {
      queryParams.append('startDate', params.startDate);
    }
    if (params.endDate) {
      queryParams.append('endDate', params.endDate);
    }

    try {
      const response = await authenticatedRequest<any>(
        `/api/admin/user/conversation/page?${queryParams.toString()}`,
        {
          method: 'GET',
        }
      );
      // API返回的数据结构：{ data: { records: [], total, size, current, pages } }
      const data = response.data;
      
      // 处理返回的数据，确保ID为字符串类型，并添加映射字段
      if (data.records) {
        data.records = data.records.map((item: any) => ({
          ...item,
          id: String(item.id), // 确保ID为字符串，避免精度丢失
          tokens: item.measuredTokens,
          question: item.title, // 映射title到question用于显示
        }));
      }
      
      return data;
    } catch (error) {
      console.error('获取对话列表失败:', error);
      throw error;
    }
  }

  /**
   * 根据ID查询对话详情
   */
  async getConversationDetail(conversationId: string): Promise<ConversationDetail> {
    try {
      const response = await authenticatedRequest<any>(
        `/api/admin/user/conversation/detail/${conversationId}`,
        {
          method: 'GET',
        }
      );
      // API返回的数据结构可能是嵌套的，根据实际情况调整
      const detail = response.data.data || response.data;
      
      // 确保ID为字符串类型，避免精度丢失
      if (detail && detail.id) {
        detail.id = String(detail.id);
      }
      
      return detail;
    } catch (error) {
      console.error('获取对话详情失败:', error);
      throw error;
    }
  }



  /**
   * 格式化日期时间
   */
  formatDateTime(dateTime?: string): string {
    if (!dateTime) return '-';
    
    try {
      const date = new Date(dateTime);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
    } catch (error) {
      return dateTime;
    }
  }

  /**
   * 格式化对话状态
   */
  formatConversationStatus(status: string): string {
    const statusMap: { [key: string]: string } = {
      'success': '成功',
      'failed': '失败',
      'pending': '处理中'
    };
    return statusMap[status] || status;
  }

  /**
   * 获取状态颜色
   */
  getStatusColor(status: string): string {
    const colorMap: { [key: string]: string } = {
      'success': 'green',
      'failed': 'red',
      'pending': 'orange'
    };
    return colorMap[status] || 'default';
  }
}

export default ConversationService.getInstance();
