import { authenticatedRequest } from './auth';
export interface TemplateItem { id: string; title: string; content?: string; createTime?: string; updateTime?: string; }
export interface TemplatePageParams { page: number; size: number; title?: string; }
export interface PageResponse<T> { records: T[]; total: number; pages: number; size: number; current: number; }
const base = '/api/admin/function/digital-template';
const normalize = (value: any): TemplateItem => ({ id: String(value.id), title: value.title, content: value.body, createTime: value.createTime, updateTime: value.updateTime });
export default class TemplateService {
  static async getPage(input: TemplatePageParams): Promise<PageResponse<TemplateItem>> {
    const query = new URLSearchParams({ pageNum: String(input.page), pageSize: String(input.size) });
    if (input.title?.trim()) query.set('title', input.title.trim());
    const { data } = await authenticatedRequest<PageResponse<any>>(base + '/page?' + query);
    return { ...data, records: (data.records || []).map(normalize) };
  }
  static async getById(id: string): Promise<TemplateItem> {
    const { data } = await authenticatedRequest<any>(base + '/' + encodeURIComponent(id));
    if (!data?.id) throw new Error('模板不存在'); return normalize(data);
  }
  static async create(input: { title: string; content?: string }): Promise<TemplateItem> {
    const { data } = await authenticatedRequest<any>(base, { method: 'POST', body: JSON.stringify({ title: input.title.trim(), body: input.content }) });
    if (!data?.id) throw new Error('后端未确认模板创建'); return normalize(data);
  }
  static async update(input: { id: string; title: string; content?: string }): Promise<void> {
    const { data } = await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id: input.id, title: input.title.trim(), body: input.content }) });
    if (data !== true) throw new Error('模板未更新，请刷新核对');
  }
  static async delete(id: string): Promise<void> {
    const { data } = await authenticatedRequest<boolean>(base + '/' + encodeURIComponent(id), { method: 'DELETE' });
    if (data !== true) throw new Error('模板未删除，请刷新核对');
  }
}
