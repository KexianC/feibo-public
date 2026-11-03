import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface BackgroundImage {
    id: string;
    title: string;
    indexImage: string;
    createTime?: string;
}
export interface PageQueryParams {
    page: number;
    pageSize: number;
    title?: string;
    startDate?: string;
    endDate?: string;
}
export interface BackgroundImageParams {
    title: string;
}
const base = '/api/admin/function/background-image';
const map = (value: any): BackgroundImage => { const r = requireRecord(value); return { id: String(r.id), title: r.name, indexImage: r.url, createTime: r.createTime }; };
export class BackgroundImageService {
    static async getBackgroundImagesPage(params: PageQueryParams): Promise<PageResponse<BackgroundImage>> { return mapMaterialPage((await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.pageSize, params.title) + (params.startDate ? '&startDate=' + encodeURIComponent(params.startDate) : '') + (params.endDate ? '&endDate=' + encodeURIComponent(params.endDate) : ''))).data, map); }
    static async getBackgroundImageById(id: string): Promise<BackgroundImage> { return map((await authenticatedRequest<any>(base + '/' + id)).data); }
    static async updateBackgroundImage(id: string, params: Partial<BackgroundImageParams>): Promise<void> { requireSaved((await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id, name: materialName(params.title || '') }) })).data); }
    static async deleteBackgroundImage(id: string): Promise<void> { requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除'); }
    static async uploadAndSave(file: File, meta: {
        title?: string;
    }): Promise<BackgroundImage> { const data = new FormData(); materialFile(data, 'file', file, '背景图片'); data.append('title', materialName(meta.title || file.name)); return map((await authenticatedRequest<any>(base + '/upload-and-save', { method: 'POST', body: data, timeoutMs: 120000 })).data); }
}
export default BackgroundImageService;
