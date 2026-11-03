import { backendRequest } from './http/backendClient';

export interface VideoProvider { provider: string; name: string; model: string; enabled: boolean; apiKeyConfigured: boolean; dailyLimit: number }
export interface VideoTask { id: string; provider: string; model: string; prompt: string; duration: number; resolution: string; ratio: string; status: string; videoUrl?: string; error?: string; createdAt: string }
export interface VideoCreate { requestId: string; provider: string; prompt: string; duration: number; resolution: string; ratio: string }
const base = '/api/v1/video-generation';
export const videoGenerationApi = {
  providers: async () => (await backendRequest<VideoProvider[]>(`${base}/providers`)).data,
  tasks: async () => (await backendRequest<VideoTask[]>(`${base}/tasks`)).data,
  create: async (body: VideoCreate) => (await backendRequest<VideoTask>(`${base}/tasks`, { method: 'POST', body: JSON.stringify(body), timeoutMs: 60000 }, { idempotencyKey: body.requestId })).data,
  refresh: async (id: string) => (await backendRequest<VideoTask>(`${base}/tasks/${encodeURIComponent(id)}`, { timeoutMs: 100000 })).data,
};
