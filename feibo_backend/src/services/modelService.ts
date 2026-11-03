import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
import { modelImportData } from './materialContracts';
export interface ModelItem {
    id: string;
    title: string;
    modelUrl?: string;
    avatarUrl?: string;
    sort?: number;
    status?: string;
    createTime?: string;
    updateTime?: string;
}
export interface ModelPageParams {
    page: number;
    size: number;
    title?: string;
}
const base = '/api/admin/function/digital-model';
const map = (value: any): ModelItem => { const r = requireRecord(value); return { id: String(r.id), title: r.modelName, modelUrl: r.modelUrl, avatarUrl: r.headUrl, sort: r.sortNum, status: r.status, createTime: r.created, updateTime: r.lastUpdateTime }; };
export default {
    async getPage(params: ModelPageParams): Promise<PageResponse<ModelItem>> {
        return mapMaterialPage((await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title, 'name'))).data, map);
    },
    async getById(id: string): Promise<ModelItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async update(payload: {
        id: string;
        title: string;
        sort?: number;
        status?: string;
    }): Promise<void> {
        const sortNum = payload.sort == null ? undefined : Number(payload.sort);
        if (sortNum != null && (!Number.isInteger(sortNum) || sortNum < 0))
            throw new Error('排序必须为非负整数');
        requireSaved((await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id: payload.id, modelName: materialName(payload.title), sortNum, status: payload.status }) })).data);
    },
    async delete(id: string): Promise<void> { requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除'); },
    async uploadJson(file: File): Promise<void> {
        if (!file || file.size === 0 || file.size > 10 * 1024 * 1024)
            throw new Error('请选择不超过 10MB 的 JSON 文件');
        const body = modelImportData(await file.text());
        const response = await authenticatedRequest<unknown>(base + '/upload-json', { method: 'POST', body, timeoutMs: 120000 });
        requireSaved(response.data, '模型导入');
    }
};
