// AI对话API服务
// 实现对话历史记录的查询和管理功能

import { API_CONFIG } from '../config/api';
import { authenticatedRequest } from './authApi';

// 基础URL配置
const BASE_URL = API_CONFIG.API_BASE_URL;

// 通用响应结构
interface ApiResponse<T = any> {
  code: string;
  info: string;
  data: T;
}

// 分页响应结构
interface PaginatedResponse<T> {
  hasNext: boolean;
  nextCursorId: number | null;
  data: T[];
}

// 对话列表项
export interface ConversationItem {
  aiConversationId: string;
  title: string;
  createTime: string;
}

// 消息记录项
export interface MessageItem {
  role: boolean; // false: 用户, true: AI助手
  content: string;
  createTime: string;
}

// 查询对话列表的参数
export interface GetConversationsParams {
  lastCursorId?: number;
  size?: number;
}

// 查询消息记录的参数
export interface GetMessagesParams {
  conversationId: string;
  lastCursorId?: number;
  size?: number;
}

/**
 * 分页查询AI对话列表
 * 使用游标分页方式获取当前用户的所有AI对话历史记录
 */
export const getConversations = async (
  params: GetConversationsParams = {}
): Promise<PaginatedResponse<ConversationItem>> => {
  const { lastCursorId, size = 10 } = params;
  
  // 构建查询参数
  const queryParams = new URLSearchParams();
  if (lastCursorId) {
    queryParams.append('lastCursorId', lastCursorId.toString());
  }
  queryParams.append('size', size.toString());
  
  const url = `/ai/conversation?${queryParams.toString()}`;
  
  try {
    const response = await authenticatedRequest<PaginatedResponse<ConversationItem>>(url, {
      method: 'GET',
    });
    
    console.log('获取对话列表成功，记录数:', response.data?.data?.length ?? 0);
    return response.data;
  } catch (error) {
    console.error('获取对话列表失败');
    throw error;
  }
};

/**
 * 分页查询指定对话内的消息记录
 * 使用游标分页方式获取某个特定对话中的所有消息
 */
export const getConversationMessages = async (
  params: GetMessagesParams
): Promise<PaginatedResponse<MessageItem>> => {
  const { conversationId, lastCursorId, size = 20 } = params;
  
  // 构建查询参数
  const queryParams = new URLSearchParams();
  if (lastCursorId) {
    queryParams.append('lastCursorId', lastCursorId.toString());
  }
  queryParams.append('size', size.toString());
  
  const url = `/ai/conversation/${conversationId}/messages?${queryParams.toString()}`;
  
  try {
    const response = await authenticatedRequest<PaginatedResponse<MessageItem>>(url, {
      method: 'GET',
    });
    
    console.log(`获取对话${conversationId}的消息记录成功，记录数:`, response.data?.data?.length ?? 0);
    return response.data;
  } catch (error) {
    console.error(`获取对话${conversationId}的消息记录失败`);
    throw error;
  }
};

/**
 * 获取对话列表（支持分页加载）
 * 优化版本：只加载第一页，支持按需加载更多
 */
export const getConversationsWithPagination = async (
  pageSize: number = 20
): Promise<PaginatedResponse<ConversationItem>> => {
  try {
    const response = await getConversations({
      size: pageSize
    });
    
    console.log(`成功获取对话列表第一页，共${response.data.length}条记录`);
    return response;
  } catch (error) {
    console.error('获取对话列表失败');
    throw error;
  }
};

/**
 * 加载更多对话列表
 */
export const loadMoreConversations = async (
  lastCursorId: number,
  pageSize: number = 20
): Promise<PaginatedResponse<ConversationItem>> => {
  try {
    const response = await getConversations({
      lastCursorId,
      size: pageSize
    });
    
    console.log(`成功加载更多对话，共${response.data.length}条记录`);
    return response;
  } catch (error) {
    console.error('加载更多对话失败');
    throw error;
  }
};

/**
 * 获取所有对话列表（支持自动分页加载）
 * 自动处理分页，返回所有对话记录
 */
export const getAllConversations = async (
  pageSize: number = 10
): Promise<ConversationItem[]> => {
  const allConversations: ConversationItem[] = [];
  let lastCursorId: number | undefined;
  let hasNext = true;
  
  try {
    while (hasNext) {
      const response = await getConversations({
        lastCursorId,
        size: pageSize
      });
      
      allConversations.push(...response.data);
      hasNext = response.hasNext;
      lastCursorId = response.nextCursorId || undefined;
      
      // 防止无限循环
      if (!hasNext || !lastCursorId) {
        break;
      }
    }
    
    console.log(`成功获取所有对话列表，共${allConversations.length}条记录`);
    return allConversations;
  } catch (error) {
    console.error('获取所有对话列表失败');
    throw error;
  }
};

/**
 * 获取指定对话的所有消息记录（支持自动分页加载）
 * 自动处理分页，返回对话中的所有消息
 */
export const getAllConversationMessages = async (
  conversationId: string,
  pageSize: number = 20
): Promise<MessageItem[]> => {
  const allMessages: MessageItem[] = [];
  let lastCursorId: number | undefined;
  let hasNext = true;
  
  try {
    while (hasNext) {
      const response = await getConversationMessages({
        conversationId,
        lastCursorId,
        size: pageSize
      });
      
      allMessages.push(...response.data);
      hasNext = response.hasNext;
      lastCursorId = response.nextCursorId || undefined;
      
      // 防止无限循环
      if (!hasNext || !lastCursorId) {
        break;
      }
    }
    
    console.log(`成功获取对话${conversationId}的所有消息，共${allMessages.length}条记录`);
    return allMessages;
  } catch (error) {
    console.error(`获取对话${conversationId}的所有消息失败`);
    throw error;
  }
};

/**
 * 将服务器消息格式转换为组件内部格式
 */
export const convertMessageToInternal = (serverMessage: MessageItem) => {
  // 尝试解析createTime为Date对象，如果失败则使用当前时间
  const parsedDate = new Date(serverMessage.createTime);
  const timestamp = isNaN(parsedDate.getTime()) ? new Date() : parsedDate;
  
  return {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 9),
    content: serverMessage.content,
    sender: serverMessage.role ? 'ai' : 'user' as 'user' | 'ai',
    timestamp: timestamp,
    agentName: serverMessage.role ? 'AI助手' : undefined,
    createTime: serverMessage.createTime // 保留原始createTime字符串
  } as any;
};

/**
 * 将服务器对话格式转换为组件内部格式
 */
export const convertConversationToInternal = (serverConversation: ConversationItem) => {
  return {
    id: serverConversation.aiConversationId,
    title: serverConversation.title,
    timestamp: serverConversation.createTime,
    active: false,
    messages: [],
    agentId: undefined
  };
};

// 修改对话标题的请求参数
// 注意：aiConversationId使用string类型以避免JavaScript大整数精度丢失问题
// JavaScript的Number类型无法精确表示超过2^53-1的整数，会导致精度丢失
export interface UpdateConversationTitleParams {
  aiConversationId: string;
  title: string;
}

/**
 * 修改对话标题
 * 更新指定对话的标题信息
 */
export const updateConversationTitle = async (
  params: UpdateConversationTitleParams
): Promise<void> => {
  const url = '/ai/conversation/update-title';
  
  try {
    await authenticatedRequest<ApiResponse>(url, {
      method: 'PUT',
      body: JSON.stringify(params),
    });
    
    console.log(`成功修改对话${params.aiConversationId}的标题，长度: ${params.title.length}`);
  } catch (error) {
    console.error(`修改对话${params.aiConversationId}标题失败`);
    throw error;
  }
};

/**
 * 删除对话
 * 删除指定的对话记录
 */
export const deleteConversation = async (
  conversationId: string
): Promise<void> => {
  const url = `/ai/conversation/${conversationId}/delete`;
  
  try {
    await authenticatedRequest<ApiResponse>(url, {
      method: 'POST',
    });
    
    console.log(`成功删除对话: ${conversationId}`);
  } catch (error) {
    console.error(`删除对话${conversationId}失败`);
    throw error;
  }
};
