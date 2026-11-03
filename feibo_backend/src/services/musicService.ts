import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface MusicItem {
    id: string;
    title: string;
    musicUrl?: string;
    duration?: number;
    createTime?: string;
    updateTime?: string;
}
export interface MusicPageParams {
    page: number;
    size: number;
    title?: string;
}
const base = '/api/admin/function/music';
const map = (value: any): MusicItem => { const r = requireRecord(value); return { id: String(r.id), title: r.name, createTime: r.createTime, musicUrl: r.url, duration: r.duration, updateTime: r.updateTime }; };
export default {
    async getPage(params: MusicPageParams): Promise<PageResponse<MusicItem>> {
        const response = await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title));
        return mapMaterialPage(response.data, map);
    },
    async getById(id: string): Promise<MusicItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async create(payload: {
        title: string;
    }, file: File): Promise<void> {
        const data = new FormData();
        data.append('title', materialName(payload.title));
        data.append('sort', '0');
        materialFile(data, 'file', file, '音乐文件');
        const response = await authenticatedRequest<any>(base + '', { method: 'POST', body: data, timeoutMs: 120000 });
        requireSaved(response.data);
    },
    async update(payload: {
        id: string;
        title: string;
    }): Promise<void> {
        const response = await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id: payload.id, name: materialName(payload.title) }) });
        requireSaved(response.data);
    },
    async delete(id: string): Promise<void> {
        requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除');
    }
};
