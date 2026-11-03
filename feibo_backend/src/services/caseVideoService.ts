import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface CaseVideoItem {
    id: string;
    title: string;
    coverUrl?: string;
    videoUrl?: string;
    scene?: string;
    industry?: string;
    sort?: number;
    isShow?: number;
    createTime?: string;
}
export interface CaseVideoPageParams {
    page: number;
    size: number;
    title?: string;
}
type Values = {
    title: string;
    scene?: string;
    industry?: string;
    sort?: number;
    isShow?: number;
};
const base = '/api/admin/function/case-video';
const map = (value: any): CaseVideoItem => { const r = requireRecord(value); return { ...r, id: String(r.id) }; };
const form = (payload: Values) => { const data = new FormData(); data.append('title', materialName(payload.title)); data.append('scene', payload.scene || ''); data.append('industry', payload.industry || ''); data.append('sort', String(payload.sort ?? 0)); data.append('isShow', String(payload.isShow ?? 1)); return data; };
export default {
    async getPage(params: CaseVideoPageParams): Promise<PageResponse<CaseVideoItem>> { return mapMaterialPage((await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title))).data, map); },
    async getById(id: string): Promise<CaseVideoItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async create(payload: Values, videoFile: File, coverFile: File): Promise<void> { const data = form(payload); materialFile(data, 'videoFile', videoFile, '视频文件'); materialFile(data, 'coverImage', coverFile, '封面图片'); requireSaved((await authenticatedRequest<boolean>(base + '/add-with-files', { method: 'POST', body: data, timeoutMs: 120000 })).data); },
    async update(payload: Values & {
        id: string;
    }, videoFile?: File, coverFile?: File): Promise<void> {
        const data = form(payload);
        data.append('id', payload.id);
        if (videoFile)
            materialFile(data, 'videoFile', videoFile, '视频文件');
        if (coverFile)
            materialFile(data, 'coverImage', coverFile, '封面图片');
        requireSaved((await authenticatedRequest<boolean>(base + '/update-with-files', { method: 'POST', body: data, timeoutMs: 120000 })).data);
    },
    async delete(id: string): Promise<void> { requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除'); }
};
