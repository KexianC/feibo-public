import { authenticatedRequest } from './auth';

export interface DigitalHumanModelItem {
  id: string;
  title?: string;
  avatarUrl?: string;
}

export interface CursorPageResponse<T> {
  records: T[];
  total?: number;
  size: number;
  current?: number;
  pages?: number;
}

class DigitalHumanModelService {
  private static instance: DigitalHumanModelService;
  private baseUrl = '/api/v1/digital-human/model';

  static getInstance(): DigitalHumanModelService {
    if (!DigitalHumanModelService.instance) DigitalHumanModelService.instance = new DigitalHumanModelService();
    return DigitalHumanModelService.instance;
  }

  /**
   * 查询当前登录用户绑定的数字人模型列表（游标分页）
   */
  async getUserModels(params: { lastCursorId?: string; size?: number }): Promise<CursorPageResponse<DigitalHumanModelItem>> {
    const qs = new URLSearchParams();
    if (typeof params.lastCursorId !== 'undefined') qs.append('lastCursorId', String(params.lastCursorId));
    qs.append('size', String(params.size ?? 12));
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/index?${qs.toString()}`, { method: 'GET' });
    const data = (resp as any)?.data ?? (resp as any);
    const clean = (u: any) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : undefined);
    const records = (data?.records || []).map((r: any): DigitalHumanModelItem => ({
      id: String(r.id),
      title: r.title ?? r.name ?? r.modelName,
      avatarUrl: clean(r.avatarUrl ?? r.headUrl ?? r.faceUrl),
    }));
    return { records, total: data?.total, size: data?.size ?? (params.size ?? 12), current: data?.current, pages: data?.pages };
  }
}

export default DigitalHumanModelService.getInstance();
