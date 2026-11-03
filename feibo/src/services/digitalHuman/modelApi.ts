import type { DigitalHuman } from '@/types';
import { backendDataRequest } from '@/services/http/backendClient';
import { fetchDigitalHumans, refreshDigitalHumans } from '@/services/digitalHuman/openPlatform';

const cleanUrl = (url?: string) =>
  (url || '')
    .toString()
    .trim()
    .replace(/^`+|`+$/g, '')
    .replace(/^"+|"+$/g, '');

const getCategoryFromGender = (
  gender: string,
  name: string,
): 'business' | 'education' | 'entertainment' => {
  const lowerName = name.toLowerCase();

  if (
    lowerName.includes('教师') ||
    lowerName.includes('老师') ||
    lowerName.includes('讲师') ||
    lowerName.includes('教授') ||
    lowerName.includes('培训') ||
    lowerName.includes('学者')
  ) {
    return 'education';
  }

  if (
    lowerName.includes('主播') ||
    lowerName.includes('演员') ||
    lowerName.includes('艺人') ||
    lowerName.includes('网红') ||
    lowerName.includes('明星') ||
    lowerName.includes('娱乐')
  ) {
    return 'entertainment';
  }

  return 'business';
};

export async function fetchMyModels(lastCursorId?: string, pageSize = 100): Promise<DigitalHuman[]> {
  const query = new URLSearchParams();
  if (lastCursorId) {
    query.append('lastCursorId', String(lastCursorId));
  }
  query.append('pageSize', String(pageSize));

  try {
    const payload = await backendDataRequest<any>(
      `/digital-human/model/index?${query.toString()}`,
      {
        method: 'GET',
        dedupeKey: `digital-human:my-models:${lastCursorId || 'first'}:${pageSize}`,
        reuseInflight: true,
        cancelKey: 'digital-human:my-models:list',
      },
      {
        auth: true,
        idempotent: false,
      },
    );

    const records = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.data)
        ? payload.data
        : Array.isArray(payload?.records)
          ? payload.records
          : [];

    return records.map((item: any) => {
      const id = String(item.id ?? item.speakerId ?? item.modelId ?? '');
      const name = String(item.modelName ?? item.speakerName ?? item.name ?? '未命名模型');
      const headUrl = cleanUrl(item.headUrl ?? item.avatar ?? item.modelUrl);
      const speakerUrl = cleanUrl(item.modelUrl ?? item.speakerUrl);
      const gender = (item.gender ?? '').toString();
      const magicAudioId = (item.magicAudioId ?? '').toString();
      const speakerWidth = Number(item.modelWidth ?? item.speakerWidth ?? item.width ?? 0) || undefined;
      const speakerHeight = Number(item.modelHeight ?? item.speakerHeight ?? item.height ?? 0) || undefined;

      return {
        id,
        name,
        avatar:
          headUrl ||
          'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNEE1NTY4Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iNDAiIHI9IjE1IiBmaWxsPSIjNkI3Mjg0Ii8+CjxwYXRoIGQ9Ik0yNSA3NUMyNSA2NS4zMzUgMzMuMzM1IDU3IDQzIDU3SDE1N0M2Ni42NjUgNTcgNzUgNjUuMzM1IDc1IDc1VjgwSDI1Vjc1WiIgZmlsbD0iIzZCNzI4NCIvPgo8L3N2Zz4K',
        category: getCategoryFromGender(gender, name),
        modelUrl: speakerUrl || `/models/${id}.glb`,
        speakerUrl,
        speakerId: item.speakerId ? String(item.speakerId) : String(item.modelId ?? ''),
        gender,
        magicAudioId,
        speakerDemoUrl: cleanUrl(item.modelDemoUrl ?? item.speakerDemoUrl) || undefined,
        speakerWidth,
        speakerHeight,
      } as DigitalHuman;
    });
  } catch (error) {
    console.error('获取我的模型数据失败:', error);
    throw error;
  }
}

export async function refreshMyModels() {
  return fetchMyModels();
}

export { fetchDigitalHumans, refreshDigitalHumans };
