import { authenticatedRequest } from './auth';
import { materialQuery, materialName, mapMaterialPage, requireRecord, requireSaved, type MaterialPage } from './materialContracts';
export type PageResponse<T> = MaterialPage<T>;
export interface VoiceItem {
    id: string;
    title: string;
    digitalVoiceId?: string;
    styleName?: string;
    gender?: number;
    isDisplay?: boolean;
    createTime?: string;
    updateTime?: string;
}
export interface VoicePageParams {
    page: number;
    size: number;
    title?: string;
}
export interface VoiceWritePayload {
    title: string;
    digitalVoiceId: string;
    gender?: number;
    styleName?: string;
    isDisplay?: boolean;
}
const base = '/api/admin/function/voice';
const map = (value: any): VoiceItem => { const r = requireRecord(value); return { id: String(r.id), title: r.name, digitalVoiceId: r.digitalVoiceId == null ? undefined : String(r.digitalVoiceId), styleName: r.styleName, gender: r.gender, isDisplay: r.isDisplay, createTime: r.createTime, updateTime: r.updateTime }; };
const writeFields = (payload: VoiceWritePayload) => {
    const digitalVoiceId = String(payload.digitalVoiceId || '').trim();
    if (!/^[1-9][0-9]{0,18}$/.test(digitalVoiceId) || BigInt(digitalVoiceId) > 9223372036854775807n)
        throw new Error('请输入有效的供应商语音 ID');
    return { name: materialName(payload.title), digitalVoiceId, gender: payload.gender, styleName: payload.styleName?.trim(), isDisplay: payload.isDisplay ?? true };
};
export default {
    async getPage(params: VoicePageParams): Promise<PageResponse<VoiceItem>> { return mapMaterialPage((await authenticatedRequest<any>(base + '/page?' + materialQuery(params.page, params.size, params.title, 'name'))).data, map); },
    async getById(id: string): Promise<VoiceItem> { return map((await authenticatedRequest<any>(base + '/' + id)).data); },
    async create(payload: VoiceWritePayload): Promise<void> {
        const response = await authenticatedRequest<boolean>(base, { method: 'POST', body: JSON.stringify(writeFields(payload)) });
        requireSaved(response.data);
    },
    async update(payload: VoiceWritePayload & { id: string; isDisplay: boolean }): Promise<void> {
        const id = String(payload.id || '').trim();
        if (!/^[1-9][0-9]{0,9}$/.test(id) || BigInt(id) > 2147483647n)
            throw new Error('语音记录 ID 无效');
        const response = await authenticatedRequest<boolean>(base, {
            method: 'PUT',
            body: JSON.stringify({ id, ...writeFields(payload), styleName: payload.styleName?.trim() ?? '', gender: payload.gender ?? 0 })
        });
        requireSaved(response.data);
    },
    async delete(id: string): Promise<void> { requireSaved((await authenticatedRequest<boolean>(base + '/' + id, { method: 'DELETE' })).data, '删除'); }
};
