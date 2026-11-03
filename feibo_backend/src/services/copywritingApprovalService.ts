import { authenticatedRequest } from './auth';

export interface CopywritingApprovalItem {
  id: string;
  copywritingId: string;
  title: string;
  submitterIdentity?: string;
  submitterName?: string;
  time?: string;
  status?: string;
  evaluation?: string;
  categoryName?: string;
  createTime?: string;
  updateTime?: string;
  flowId?: string;
}

export interface CopywritingDetail extends CopywritingApprovalItem {
  content?: string;
}

export interface ApprovalPageParams {
  page: number;
  size: number;
  title?: string;
  status?: number;
  submitterName?: string;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  pages: number;
  size: number;
  current: number;
}

class CopywritingApprovalService {
  private static instance: CopywritingApprovalService;
  private baseUrl = '/api/admin/function/copywriting-approval';
  private expertBase = '/api/v1/copywriting';
  private adminCopywritingBase = '/api/admin/function/copywriting';

  static getInstance(): CopywritingApprovalService {
    if (!CopywritingApprovalService.instance) CopywritingApprovalService.instance = new CopywritingApprovalService();
    return CopywritingApprovalService.instance;
  }

  /**
   * 分页查询文案审批列表
   */
  async getPage(params: ApprovalPageParams): Promise<PageResponse<CopywritingApprovalItem>> {
    const qs = new URLSearchParams();
    qs.append('pageNum', String(params.page));
    qs.append('pageSize', String(params.size));
    if (params.title) qs.append('title', params.title);
    if (params.submitterName) qs.append('submitterName', params.submitterName);
    if (typeof params.status !== 'undefined') qs.append('status', String(params.status));
    const resp = await authenticatedRequest<PageResponse<any>>(`${this.baseUrl}/page?${qs.toString()}`, { method: 'GET' });
    const data = resp.data as PageResponse<any>;
    const records = (data.records || []).map((r: any): CopywritingApprovalItem => ({
      id: String(r.id),
      copywritingId: String(r.copywritingId ?? r.id),
      title: r.title ?? r.name ?? r.copywritingTitle,
      submitterIdentity: r.submitterIdentity,
      submitterName: r.submitterName,
      time: r.time ?? r.createTime ?? r.created,
      status: r.status,
      evaluation: r.evaluation,
      categoryName: r.categoryName ?? r.category ?? r.typeName,
      createTime: r.createTime ?? r.created,
      updateTime: r.updateTime ?? r.lastUpdateTime,
      flowId: typeof r.flowId !== 'undefined' ? String(r.flowId) : (typeof r.copywritingApprovalFlowId !== 'undefined' ? String(r.copywritingApprovalFlowId) : undefined),
    }));
    return { records, total: data.total, pages: data.pages, size: data.size, current: data.current };
  }

  /**
   * 查询文案详情
   */
  async getDetail(copywritingId: string): Promise<CopywritingDetail> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/detail/${copywritingId}`, { method: 'GET' });
    const r = (resp as any)?.data ?? (resp as any);
    return {
      copywritingId: String(r.copywritingId ?? r.id ?? copywritingId),
      id: String(r.id ?? copywritingId),
      title: r.title ?? r.name ?? r.copywritingTitle ?? '',
      categoryName: r.categoryName ?? r.category ?? r.typeName,
      status: r.status ?? r.approvalStatus ?? r.auditStatus,
      content: r.content ?? r.text ?? r.body ?? r.copywritingContent,
      createTime: r.createTime ?? r.created,
      updateTime: r.updateTime ?? r.lastUpdateTime,
    };
  }

  /**
   * 专家认领审核任务
   */
  /**
   * 专家认领审核任务
   */
  async claimByExpert(payload: { comment?: string; copywritingApprovalFlowId: string; copywritingId: string }): Promise<{ code: number | string; msg?: string; info?: string; data: any }> {
    const body: any = {
      comment: payload.comment ?? '',
      copywritingApprovalFlowId: payload.copywritingApprovalFlowId,
      copywritingId: payload.copywritingId,
    };
    return authenticatedRequest<any>(`${this.expertBase}/claim/by-expert`, { method: 'POST', body: JSON.stringify(body) });
  }

  /**
   * 专家审核通过
   */
  /**
   * 专家审核通过
   */
  async approveByExpert(payload: { comment?: string; copywritingApprovalFlowId: string; copywritingId: string }): Promise<{ code: number | string; msg?: string; info?: string; data: any }> {
    const body: any = {
      comment: payload.comment ?? '',
      copywritingApprovalFlowId: payload.copywritingApprovalFlowId,
      copywritingId: payload.copywritingId,
    };
    return authenticatedRequest<any>(`${this.expertBase}/approve/by-expert`, { method: 'POST', body: JSON.stringify(body) });
  }

  /**
   * 平台审核通过
   */
  /**
   * 平台审核通过
   */
  async approveByPlatform(payload: { comment?: string; copywritingApprovalFlowId: string; copywritingId: string }): Promise<{ code: number | string; msg?: string; info?: string; data: any }> {
    const body: any = {
      comment: payload.comment ?? '',
      copywritingApprovalFlowId: payload.copywritingApprovalFlowId,
      copywritingId: payload.copywritingId,
    };
    return authenticatedRequest<any>(`${this.expertBase}/approve/by-platform`, { method: 'POST', body: JSON.stringify(body) });
  }

  /**
   * 删除文案（逻辑删除）
   * @param id 文案ID（字符串，避免长整型精度丢失）
   */
  async deleteById(id: string): Promise<void> {
    const response = await authenticatedRequest<boolean>(`${this.adminCopywritingBase}/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (response.data !== true) throw new Error('后端未删除文案，请刷新后核对');
  }
}

export default CopywritingApprovalService.getInstance();
