import type { DigitalHuman } from '@/types';
import { requestJson } from '@/services/http/client';
import { backendDataRequest } from '@/services/http/backendClient';
import type {
  ApiDigitalHuman,
  ApiResponse,
  VideoComposeResultResponse,
  VideoInfo,
} from '@/services/digitalHuman/types';

const OPEN_PLATFORM_BASE_URL = 'https://openman.weta365.com/metaman/open';

const fallbackDigitalHumans: DigitalHuman[] = [
  {
    id: 'fallback-1',
    name: '商务男士',
    avatar:
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjMkQ0Mzc0Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjNjM3M0ZGIi8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iIzYzNzNGRiIvPgo8L3N2Zz4K',
    category: 'business',
    modelUrl: '/models/business-man.glb',
  },
  {
    id: 'fallback-2',
    name: '商务女士',
    avatar:
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNzQ0MjEwIi8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjRkY2Mzc0Ii8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iI0ZGNjM3NCIvPgo8L3N2Zz4K',
    category: 'business',
    modelUrl: '/models/business-woman.glb',
  },
  {
    id: 'fallback-3',
    name: '教师',
    avatar:
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjMDY0RTNBIi8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iMzgiIHI9IjE4IiBmaWxsPSIjMTBCOTgxIi8+CjxwYXRoIGQ9Ik0yMCA4MEMyMCA2OC45NTQzIDI4Ljk1NDMgNjAgNDAgNjBINjBDNzEuMDQ1NyA2MCA4MCA2OC45NTQzIDgwIDgwVjg1SDIwVjgwWiIgZmlsbD0iIzEwQjk4MSIvPgo8L3N2Zz4K',
    category: 'education',
    modelUrl: '/models/teacher.glb',
  },
];

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

  return gender ? 'business' : 'business';
};

function mapApiDigitalHuman(apiDigitalHuman: ApiDigitalHuman): DigitalHuman {
  return {
    id: apiDigitalHuman.id,
    name: apiDigitalHuman.speakerName || '未命名数字人',
    avatar:
      apiDigitalHuman.headUrl?.trim() ||
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIiBmaWxsPSIjNEE1NTY4Ii8+CjxjaXJjbGUgY3g9IjUwIiBjeT0iNDAiIHI9IjE1IiBmaWxsPSIjNkI3Mjg0Ii8+CjxwYXRoIGQ9Ik0yNSA3NUMyNSA2NS4zMzUgMzMuMzM1IDU3IDQzIDU3SDE1N0M2Ni42NjUgNTcgNzUgNjUuMzM1IDc1IDc1VjgwSDI1Vjc1WiIgZmlsbD0iIzZCNzI4NCIvPgo8L3N2Zz4K',
    category: getCategoryFromGender(apiDigitalHuman.gender, apiDigitalHuman.speakerName || ''),
    modelUrl: `/models/${apiDigitalHuman.id}.glb`,
    speakerUrl: apiDigitalHuman.speakerUrl?.trim(),
    speakerId: apiDigitalHuman.speakerId,
    gender: apiDigitalHuman.gender,
    magicAudioId: apiDigitalHuman.magicAudioId,
    speakerDemoUrl: apiDigitalHuman.speakerDemoUrl?.trim(),
    speakerWidth: apiDigitalHuman.speakerWidth,
    speakerHeight: apiDigitalHuman.speakerHeight,
  };
}

export async function fetchDigitalHumans(): Promise<DigitalHuman[]> {
  try {
    const { response, data } = await requestJson<ApiResponse>(
      `${OPEN_PLATFORM_BASE_URL}/avatar/infos?customType=sys&current=1&size=139&speakerName=元`,
      {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
        dedupeKey: 'digital-human:sys-models',
        reuseInflight: true,
        cancelKey: 'digital-human:sys-models',
      },
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (data.code !== 200 || !data.data?.records) {
      throw new Error(data.message || '获取数字人数据失败');
    }

    return data.data.records.map(mapApiDigitalHuman);
  } catch (error) {
    console.error('获取数字人数据失败:', error);
    return fallbackDigitalHumans;
  }
}

export async function refreshDigitalHumans() {
  return fetchDigitalHumans();
}

export async function getVideoComposeResult(
  taskId: string,
  signal?: AbortSignal,
): Promise<VideoComposeResultResponse['data']> {
  return backendDataRequest<VideoComposeResultResponse['data']>(
    `/digital-human/open/video/result/${encodeURIComponent(taskId)}`,
    {
      method: 'GET',
      signal,
      dedupeKey: `digital-human:compose-result:${taskId}`,
      reuseInflight: true,
    },
    {
      auth: true,
      idempotent: false,
    },
  );
}

export async function pollVideoComposeResult(
  taskId: string,
  onProgress?: (progress: number, status: string) => void,
  interval = 2000,
  signal?: AbortSignal,
): Promise<VideoComposeResultResponse['data']> {
  return new Promise((resolve, reject) => {
    let timeoutId: number | undefined;

    const handleAbort = () => {
      if (timeoutId) {
        window.clearTimeout(timeoutId);
      }
      reject(new Error('轮询已取消'));
    };

    if (signal) {
      if (signal.aborted) {
        reject(new Error('轮询已取消'));
        return;
      }

      signal.addEventListener('abort', handleAbort, { once: true });
    }

    const poll = async () => {
      if (signal?.aborted) {
        handleAbort();
        return;
      }

      try {
        const result = await getVideoComposeResult(taskId, signal);
        const mappedStatus = result.status === 'ing' ? 'ing' : result.status;

        onProgress?.(result.progress || 0, mappedStatus);

        if (result.status === 'suc' || result.status === 'completed') {
          signal?.removeEventListener('abort', handleAbort);
          resolve(result);
          return;
        }

        if (result.status === 'failed' || result.status === 'fail') {
          signal?.removeEventListener('abort', handleAbort);
          reject(new Error(`合成失败：${result.errorMessage || result.statusDesc || '服务未返回具体失败原因'}`));
          return;
        }

        timeoutId = window.setTimeout(poll, interval);
      } catch (error) {
        signal?.removeEventListener('abort', handleAbort);
        reject(error);
      }
    };

    void poll();
  });
}

export async function getVideoInfo(videoId: string): Promise<VideoInfo> {
  const result = await getVideoComposeResult(videoId);

  return {
    id: videoId,
    taskId: result.taskId || videoId,
    name: result.name || `视频_${videoId}`,
    videoUrl: result.resultUrl || result.videoUrl || '',
    coverUrl: result.coverUrl,
    thumbnailUrl: result.thumbnailUrl,
    duration: result.duration,
    width: result.width,
    height: result.height,
    status: result.status === 'ing' ? 'processing' : result.status,
    progress: result.progress,
    createTime: result.created || result.createTime || new Date().toISOString(),
    updateTime: result.lastUpdateTime || result.updateTime,
    errorMessage: result.errorMessage || result.statusDesc,
    created: result.created,
    lastUpdateTime: result.lastUpdateTime,
  };
}

export async function downloadVideo(videoUrl: string, filename = 'video.mp4') {
  try {
    const response = await fetch(videoUrl);

    if (!response.ok) {
      throw new Error(`下载失败: ${response.status} ${response.statusText}`);
    }

    const blob = await response.blob();
    const videoBlob = new Blob([blob], { type: 'video/mp4' });
    const blobUrl = URL.createObjectURL(videoBlob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
  } catch (error) {
    console.error('视频下载失败:', error);
    const link = document.createElement('a');
    link.href = videoUrl;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
}
