import { authenticatedRequest } from './auth';
import type { ResolveFailedVideoInput } from '@/components/ResolveFailedVideoModal';

export interface PendingDigitalQuota {
  work_record_id: string;
  external_id: string;
  user_id: string;
  title: string;
  created_at: string;
  status: string;
}

// 数字作品数据接口定义（与后端返回字段保持一致）
export interface DigitalWork {
  audit_id: number;
  work_record_id: string;
  external_id: string | null;
  user_id?: string;
  title: string | null;
  url: string | null;
  duration: number | null;
  username: string;
  phone: string;
  bill: number | null;
  status: string;
  failure_reason: string | null;
  create_time: string | null;
  compose_params: string;
  video_time: number | null;
  request_sha256: string;
  capture_source: 'legacy_body' | 'provider_request';
  submitted_at: string;
  archived_only: boolean;
}

// 分页查询参数接口
export interface PageQueryParams {
  pageNum: number;
  pageSize: number;
  keyword?: string;
}

export interface VideoStatisticsParams {
  startDate?: string;
  endDate?: string;
}

export interface VideoStatistics {
  total: number;
  success: number;
  failed: number;
  processing: number;
  totalDuration: number;
}

// 分页响应接口
export interface PageResponse<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

// API响应接口
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

class DigitalWorksService {
  // 统一使用相对路径，认证与基础URL由 authenticatedRequest 处理
  private baseUrl = `/api/admin/user/digital-works`;

  /**
   * 分页查询数字作品列表
   * @param params 查询参数
   * @returns Promise<PageResponse<DigitalWork>>
   */
  async getDigitalWorksPage(params: PageQueryParams): Promise<PageResponse<DigitalWork>> {
    const queryParams = new URLSearchParams();
    // 使用后端定义的分页参数名：pageNum、pageSize
    queryParams.append('pageNum', params.pageNum.toString());
    queryParams.append('pageSize', params.pageSize.toString());
    queryParams.append('_t', String(Date.now()));
    
    if (params.keyword) {
      queryParams.append('keyword', params.keyword);
    }

    const response = await authenticatedRequest<PageResponse<DigitalWork>>(
      `${this.baseUrl}/page?${queryParams.toString()}`,
      { method: 'GET' }
    );

    // authenticatedRequest 返回标准结构 { code, msg, data }
    return response.data;
  }

  async getPendingQuota(): Promise<PendingDigitalQuota[]> {
    return (await authenticatedRequest<PendingDigitalQuota[]>(`${this.baseUrl}/pending-quota?_t=${Date.now()}`)).data;
  }

  async resolveFailed(workRecordId: string, input: ResolveFailedVideoInput): Promise<void> {
    await authenticatedRequest(`${this.baseUrl}/${encodeURIComponent(workRecordId)}/resolve-failed`, {
      method: 'POST', body: JSON.stringify(input),
    });
  }

  async getVideoStatistics(params: VideoStatisticsParams): Promise<VideoStatistics> {
    const queryParams = new URLSearchParams();
    if (params.startDate) queryParams.append('startDate', params.startDate);
    if (params.endDate) queryParams.append('endDate', params.endDate);
    const suffix = queryParams.toString() ? `?${queryParams.toString()}` : '';
    const response = await authenticatedRequest<VideoStatistics>(
      `${this.baseUrl}/statistics${suffix}`,
      { method: 'GET' }
    );
    const data = response.data || ({} as VideoStatistics);
    return {
      total: Number(data.total || 0),
      success: Number(data.success || 0),
      failed: Number(data.failed || 0),
      processing: Number(data.processing || 0),
      totalDuration: Number(data.totalDuration || 0),
    };
  }

  /**
   * 根据ID删除数字作品
   * @param id 数字作品ID
   * @returns Promise<void>
   */
  async deleteDigitalWork(id: string): Promise<void> {
    const response = await authenticatedRequest<void>(
      `${this.baseUrl}/${id}`,
      { method: 'DELETE' }
    );
    // 若业务失败，authenticatedRequest 已抛错；此处无需额外处理
  }

  /**
   * 批量删除数字作品
   * @param ids 数字作品ID数组
   * @returns Promise<void>
   */
  async batchDeleteDigitalWorks(ids: string[]): Promise<void> {
    const response = await authenticatedRequest<void>(
      `${this.baseUrl}/batch`,
      {
        method: 'DELETE',
        body: JSON.stringify({ ids }),
      }
    );
    // 统一认证与错误处理由 authenticatedRequest 负责
  }
}

export const digitalWorksService = new DigitalWorksService();
