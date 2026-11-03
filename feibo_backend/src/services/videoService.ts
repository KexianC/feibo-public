import { authenticatedRequest } from './auth';
import { materialQuery, materialName, materialFile, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface VideoItem {
    id: string;
    title: string;
    videoUrl?: string;
    coverUrl?: string;
    duration?: string;
    createTime?: string;
    updateTime?: string;
}
export interface VideoPageParams {
    page: number;
    size: number;
    title?: string;
}
const base = '/api/admin/function/video';
const map = (value: any): VideoItem => { const r = requireRecord(value); return { id: String(r.id), title: r.title, createTime: r.createTime, videoUrl: r.videoUrl, coverUrl: r.coverUrl, duration: r.duration }; };
export default {
    async getPage(params: VideoPageParams): Promise<PageResponse<VideoItem>> {
        const response = await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title));
        return mapMaterialPage(response.data, map);
    },
    async getById(id: string): Promise<VideoItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async create(payload: {
        title: string;
    }, file: File, coverFile: File): Promise<void> {
        const data = new FormData();
        data.append('title', materialName(payload.title));
        materialFile(data, 'file', file, '视频文件');
        materialFile(data, 'coverFile', coverFile, '封面图片');
        const response = await authenticatedRequest<any>(base + '', { method: 'POST', body: data, timeoutMs: 120000 });
        requireSaved(response.data);
    },
    async update(payload: {
        id: string;
        title: string;
    }): Promise<void> {
        const response = await authenticatedRequest<boolean>(base, { method: 'PUT', body: JSON.stringify({ id: payload.id, title: materialName(payload.title) }) });
        requireSaved(response.data);
    },
    async delete(id: string): Promise<void> {
        requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除');
    }
};
