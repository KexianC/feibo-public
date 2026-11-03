import { authenticatedRequest } from './auth';
export interface CopywritingCategoryItem { id: string; title: string; isShow: number; createTime?: string; updateTime?: string; }
export interface CopywritingCategoryPageParams { page: number; size: number; title?: string; }
export interface PageResponse<T> { records: T[]; total: number; pages: number; size: number; current: number; }
const base = '/api/admin/function/copywriting-category';
const normalize = (item: any): CopywritingCategoryItem => ({ id: String(item.id), title: item.name, isShow: item.status === false || item.status === 0 ? 1 : 0, createTime: item.createTime, updateTime: item.updateTime });
export default class CopywritingCategoryService {
  static async getPage(params: CopywritingCategoryPageParams): Promise<PageResponse<CopywritingCategoryItem>> {
    const query = new URLSearchParams({ pageNum: String(params.page), pageSize: String(params.size) });
    if (params.title?.trim()) query.set('title', params.title.trim());
    const { data } = await authenticatedRequest<PageResponse<any>>(base + '/page?' + query);
    return { ...data, records: (data.records || []).map(normalize) };
  }
  static async getById(id: string): Promise<CopywritingCategoryItem> {
    const { data } = await authenticatedRequest<any>(base + '/' + encodeURIComponent(id));
    if (!data?.id) throw new Error('分类不存在'); return normalize(data);
  }
  static async create(input: { title: string; isShow?: number }): Promise<CopywritingCategoryItem> {
    const { data } = await authenticatedRequest<any>(base, { method: 'POST', body: JSON.stringify({ name: input.title.trim(), status: input.isShow === 0 }) });
    if (!data?.id) throw new Error('后端未确认分类创建'); return normalize(data);
  }
  static async update(input: { id: string; title: string; isShow?: number }): Promise<void> {
    const { data } = await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id: input.id, name: input.title.trim(), status: input.isShow === 0 }) });
    if (data !== true) throw new Error('分类未更新，请刷新后重试');
  }
  static async delete(id: string): Promise<void> {
    const { data } = await authenticatedRequest<boolean>(base + '/' + encodeURIComponent(id), { method: 'DELETE' });
    if (data !== true) throw new Error('分类未删除，请刷新后重试');
  }
}
