import { authenticatedRequest } from './auth';

export interface AdminDigitalModelItem {
  id: string;
  title?: string;
  avatarUrl?: string;
  modelCode?: string;
  hospital?: string;
}

/**
 * 管理端数字模型服务
 */
class AdminDigitalModelService {
  private static instance: AdminDigitalModelService;
  private baseUrl = '/api/admin/digital-model';

  static getInstance(): AdminDigitalModelService {
    if (!AdminDigitalModelService.instance) AdminDigitalModelService.instance = new AdminDigitalModelService();
    return AdminDigitalModelService.instance;
  }

  /**
   * 查询所有数字模型
   */
  async getAllModels(): Promise<AdminDigitalModelItem[]> {
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/page`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const clean = (u: any) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : undefined);
    const list = Array.isArray(d?.records) ? d.records : (Array.isArray(d) ? d : []);
    return list.map((r: any) => ({
      id: String(r.id ?? r.modelId),
      title: r.title ?? r.modelName ?? r.name,
      avatarUrl: clean(r.modelUrl ?? r.avatarUrl ?? r.headUrl ?? r.faceUrl),
      modelCode: typeof r.modelId !== 'undefined' ? String(r.modelId) : undefined,
      hospital: r.hospital ?? r.hospitalName ?? r.orgName ?? r.organizationName,
    }));
  }

  /**
   * 根据用户ID查询绑定模型
   */
  async getUserBoundModels(userId: string): Promise<AdminDigitalModelItem[]> {
    const qs = new URLSearchParams();
    qs.append('userId', userId);
    const resp = await authenticatedRequest<any>(`${this.baseUrl}/user-bound?${qs.toString()}`, { method: 'GET' });
    const d = (resp as any)?.data ?? (resp as any);
    const clean = (u: any) => (typeof u === 'string' ? u.replace(/[`'\"]/g, '').trim() : undefined);
    const list = Array.isArray(d) ? d : (d?.records || []);
    return list.map((r: any) => ({
      id: String(r.id ?? r.modelId),
      title: r.title ?? r.modelName ?? r.name,
      avatarUrl: clean(r.modelUrl ?? r.avatarUrl ?? r.headUrl ?? r.faceUrl),
      modelCode: typeof r.modelId !== 'undefined' ? String(r.modelId) : undefined,
      hospital: r.hospital ?? r.hospitalName ?? r.orgName ?? r.organizationName,
    }));
  }

  /**
   * 修改用户模型绑定关系
   */
  async updateUserBindings(userId: string, modelIds: string[]): Promise<void> {
    const qs = new URLSearchParams();
    qs.append('userId', userId);
    const onlyDigits = Array.from(new Set(modelIds)).filter(id => typeof id === 'string' && /^\d+$/.test(id));
    onlyDigits.forEach(id => qs.append('modelIds', id));
    await authenticatedRequest<void>(`${this.baseUrl}/user-bindings?${qs.toString()}`, { method: 'POST' });
  }
}

export default AdminDigitalModelService.getInstance();
