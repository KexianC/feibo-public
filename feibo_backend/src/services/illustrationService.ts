import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface IllustrationItem {
    id: string;
    title: string;
    indexImage?: string;
    createTime?: string;
    updateTime?: string;
}
export interface IllustrationPageParams {
    page: number;
    size: number;
    title?: string;
}
const base = '/api/admin/function/illustration';
const map = (value: any): IllustrationItem => { const r = requireRecord(value); return { id: String(r.id), title: r.name, createTime: r.createTime, indexImage: r.url }; };
export default {
    async getPage(params: IllustrationPageParams): Promise<PageResponse<IllustrationItem>> {
        const response = await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title));
        return mapMaterialPage(response.data, map);
    },
    async getById(id: string): Promise<IllustrationItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async create(payload: {
        title: string;
    }, file: File): Promise<void> {
        const data = new FormData();
        data.append('title', materialName(payload.title));
        materialFile(data, 'file', file, '插图');
        const response = await authenticatedRequest<any>(base + '/add-with-image', { method: 'POST', body: data, timeoutMs: 120000 });
        requireRecord(response.data);
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
